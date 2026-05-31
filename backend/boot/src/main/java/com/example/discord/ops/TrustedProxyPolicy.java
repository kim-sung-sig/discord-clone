package com.example.discord.ops;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.regex.Pattern;
import org.springframework.core.env.Environment;

final class TrustedProxyPolicy {
    static final String PROPERTY_NAME = "discord.trusted-proxy.cidrs";

    private static final Pattern DIGITS = Pattern.compile("\\d+");
    private static final Pattern IPV4_MAPPED = Pattern.compile("^::ffff:(\\d{1,3}(?:\\.\\d{1,3}){3})$", Pattern.CASE_INSENSITIVE);

    private final List<TrustedProxyRule> rules;

    private TrustedProxyPolicy(List<TrustedProxyRule> rules) {
        this.rules = List.copyOf(rules);
    }

    static TrustedProxyPolicy from(Environment environment) {
        return from(environment.getProperty(PROPERTY_NAME, ""));
    }

    static TrustedProxyPolicy from(String rawRules) {
        return new TrustedProxyPolicy(rawRules(rawRules)
            .map(TrustedProxyPolicy::parseRule)
            .flatMap(Optional::stream)
            .toList());
    }

    static List<String> invalidRules(String rawRules) {
        return rawRules(rawRules)
            .filter(entry -> parseRule(entry).isEmpty())
            .toList();
    }

    boolean isConfigured() {
        return !rules.isEmpty();
    }

    boolean isTrustedProxy(String remoteAddr) {
        Optional<NormalizedIp> remoteIp = normalizeIp(remoteAddr);
        return remoteIp.isPresent() && rules.stream().anyMatch(rule -> rule.matches(remoteIp.get()));
    }

    Optional<String> forwardedClientIp(String forwardedFor, String realIp) {
        Optional<String> forwardedIp = firstForwardedIp(forwardedFor);
        if (forwardedIp.isPresent()) {
            return forwardedIp;
        }
        return normalizeIp(realIp).map(NormalizedIp::value);
    }

    static Optional<String> firstForwardedIp(String forwardedFor) {
        if (forwardedFor == null || forwardedFor.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(forwardedFor.split(","))
            .map(TrustedProxyPolicy::normalizeIp)
            .flatMap(Optional::stream)
            .map(NormalizedIp::value)
            .findFirst();
    }

    static Optional<String> normalizeIpAddress(String value) {
        return normalizeIp(value).map(NormalizedIp::value);
    }

    private static java.util.stream.Stream<String> rawRules(String rawRules) {
        if (rawRules == null || rawRules.isBlank()) {
            return java.util.stream.Stream.empty();
        }
        return Arrays.stream(rawRules.split(","))
            .map(String::trim)
            .filter(entry -> !entry.isEmpty());
    }

    private static Optional<TrustedProxyRule> parseRule(String entry) {
        Optional<NormalizedIp> exactIp = normalizeIp(entry);
        if (exactIp.isPresent()) {
            return Optional.of(new ExactIpRule(exactIp.get().value()));
        }

        String[] parts = entry.split("/", -1);
        if (parts.length != 2 || !DIGITS.matcher(parts[1]).matches()) {
            return Optional.empty();
        }
        Optional<NormalizedIp> network = normalizeIp(parts[0]);
        OptionalInt parsedPrefixLength = parseInt(parts[1]);
        if (parsedPrefixLength.isEmpty()) {
            return Optional.empty();
        }
        int prefixLength = parsedPrefixLength.getAsInt();
        if (network.isEmpty() || network.get().ipv4Number().isEmpty() || prefixLength < 0 || prefixLength > 32) {
            return Optional.empty();
        }
        return Optional.of(new Ipv4CidrRule(network.get().ipv4Number().getAsLong(), prefixLength));
    }

    private static Optional<NormalizedIp> normalizeIp(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            return Optional.empty();
        }

        String candidate = removeIpv6Brackets(trimmed);
        candidate = normalizeIpv4WithOptionalPort(candidate);
        var mapped = IPV4_MAPPED.matcher(candidate);
        if (mapped.matches()) {
            candidate = mapped.group(1);
        }

        Optional<Long> ipv4Number = ipv4ToLong(candidate);
        if (ipv4Number.isPresent()) {
            return Optional.of(new NormalizedIp(longToIpv4(ipv4Number.get()), ipv4Number.get()));
        }
        if (!candidate.contains(":") || candidate.contains("%")) {
            return Optional.empty();
        }

        try {
            InetAddress address = InetAddress.getByName(candidate);
            if (address.getAddress().length != 16) {
                return Optional.empty();
            }
            return Optional.of(new NormalizedIp(address.getHostAddress().toLowerCase(Locale.ROOT), null));
        } catch (UnknownHostException exception) {
            return Optional.empty();
        }
    }

    private static String removeIpv6Brackets(String value) {
        if (value.startsWith("[") && value.contains("]")) {
            return value.substring(1, value.indexOf(']'));
        }
        return value;
    }

    private static String normalizeIpv4WithOptionalPort(String value) {
        if (value.chars().filter(ch -> ch == ':').count() != 1) {
            return value;
        }
        String host = value.substring(0, value.indexOf(':'));
        return ipv4ToLong(host).isPresent() ? host : value;
    }

    private static Optional<Long> ipv4ToLong(String value) {
        String[] octets = value.split("\\.", -1);
        if (octets.length != 4) {
            return Optional.empty();
        }
        long result = 0;
        for (String octet : octets) {
            if (!DIGITS.matcher(octet).matches()) {
                return Optional.empty();
            }
            OptionalInt parsedOctet = parseInt(octet);
            if (parsedOctet.isEmpty()) {
                return Optional.empty();
            }
            int parsed = parsedOctet.getAsInt();
            if (parsed < 0 || parsed > 255) {
                return Optional.empty();
            }
            result = (result << 8) + parsed;
        }
        return Optional.of(result);
    }

    private static String longToIpv4(long value) {
        return "%d.%d.%d.%d".formatted(
            (value >>> 24) & 0xff,
            (value >>> 16) & 0xff,
            (value >>> 8) & 0xff,
            value & 0xff
        );
    }

    private static OptionalInt parseInt(String value) {
        try {
            return OptionalInt.of(Integer.parseInt(value));
        } catch (NumberFormatException exception) {
            return OptionalInt.empty();
        }
    }

    private interface TrustedProxyRule {
        boolean matches(NormalizedIp remoteIp);
    }

    private record ExactIpRule(String value) implements TrustedProxyRule {
        @Override
        public boolean matches(NormalizedIp remoteIp) {
            return value.equals(remoteIp.value());
        }
    }

    private record Ipv4CidrRule(long network, int prefixLength) implements TrustedProxyRule {
        @Override
        public boolean matches(NormalizedIp remoteIp) {
            if (remoteIp.ipv4Number().isEmpty()) {
                return false;
            }
            long mask = prefixLength == 0
                ? 0
                : (0xffffffffL << (32 - prefixLength)) & 0xffffffffL;
            return (remoteIp.ipv4Number().getAsLong() & mask) == (network & mask);
        }
    }

    private record NormalizedIp(String value, Long ipv4Value) {
        OptionalLong ipv4Number() {
            return ipv4Value == null ? OptionalLong.empty() : OptionalLong.of(ipv4Value);
        }
    }
}
