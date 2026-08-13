function Get-ShardId {
    [CmdletBinding()]
    param(
        [string] $ChatRoomId,

        [Parameter(Mandatory)]
        [datetime] $EventDate,

        [Parameter(Mandatory)]
        [int] $ShardCount
    )

    if ([string]::IsNullOrWhiteSpace($ChatRoomId)) {
        throw [ArgumentException]::new('ChatRoomId must not be empty.', 'ChatRoomId')
    }

    if ($EventDate.Kind -ne [DateTimeKind]::Utc) {
        throw [ArgumentException]::new('EventDate must be UTC.', 'EventDate')
    }

    if ($ShardCount -le 0) {
        throw [ArgumentException]::new('ShardCount must be greater than zero.', 'ShardCount')
    }

    if ($ShardCount -eq 1) {
        return 0
    }

    $key = '{0}|{1}' -f $ChatRoomId, $EventDate.ToString('o', [Globalization.CultureInfo]::InvariantCulture)
    $bytes = [Text.Encoding]::UTF8.GetBytes($key)
    $digest = [Security.Cryptography.SHA256]::HashData($bytes)
    $hash = [BitConverter]::ToUInt32($digest, 0)

    return [int] ($hash % [uint32] $ShardCount)
}
