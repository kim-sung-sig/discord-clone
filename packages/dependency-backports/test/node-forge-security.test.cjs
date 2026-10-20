'use strict';

const assert = require('node:assert/strict');
const test = require('node:test');
const forge = require('node-forge');

function signatureForDigestInfo(digestInfo) {
  const keySize = 256;
  const paddingLength = keySize - 3 - digestInfo.length;
  assert.ok(paddingLength >= 8);
  return Buffer.concat([
    Buffer.from([0, 1]),
    Buffer.alloc(paddingLength, 0xff),
    Buffer.from([0]),
    digestInfo
  ]);
}

function verifyEncodedDigestInfo(digestInfo, message, options) {
  const modulus = new forge.jsbn.BigInteger('ff'.repeat(256), 16);
  const publicKey = forge.pki.setRsaPublicKey(modulus, new forge.jsbn.BigInteger('1', 10));
  const digest = forge.md.sha256.create();
  digest.update(message, 'utf8');
  return publicKey.verify(
    digest.digest().getBytes(),
    signatureForDigestInfo(digestInfo).toString('binary'),
    undefined,
    options
  );
}

test('rejects non-canonical NULL parameters carrying attacker-controlled bytes', () => {
  const digest = Buffer.from('7509e5bda0c762d2bac7f90d758b5b2263fa01ccbc542ab5e3df163be08e6ca9', 'hex');
  const digestInfo = Buffer.concat([
    Buffer.from('3033300f06096086480165030402010502beef0420', 'hex'),
    digest
  ]);
  assert.throws(() => verifyEncodedDigestInfo(digestInfo, 'hello world!'), /valid RSASSA-PKCS1-v1_5 DigestInfo/);
});

test('rejects non-minimal lengths and unvalidated nested elements', () => {
  const digest = Buffer.from('7509e5bda0c762d2bac7f90d758b5b2263fa01ccbc542ab5e3df163be08e6ca9', 'hex');
  const nonMinimalLengths = Buffer.concat([
    Buffer.from('30813230810d060960864801650304020105000420', 'hex'),
    digest
  ]);
  const extraNestedElement = Buffer.concat([
    Buffer.from('30343010060960864801650304020105000401ff0420', 'hex'),
    digest
  ]);
  assert.throws(() => verifyEncodedDigestInfo(nonMinimalLengths, 'hello world!'), /valid RSASSA-PKCS1-v1_5 DigestInfo/);
  assert.throws(() => verifyEncodedDigestInfo(extraNestedElement, 'hello world!'), /valid RSASSA-PKCS1-v1_5 DigestInfo/);
});

test('continues to accept canonical SHA-256 DigestInfo with NULL parameters', () => {
  const digest = Buffer.from('7509e5bda0c762d2bac7f90d758b5b2263fa01ccbc542ab5e3df163be08e6ca9', 'hex');
  const digestInfo = Buffer.concat([
    Buffer.from('3031300d060960864801650304020105000420', 'hex'),
    digest
  ]);
  assert.equal(verifyEncodedDigestInfo(digestInfo, 'hello world!'), true);
});

test('continues to accept canonical SHA-256 DigestInfo without parameters', () => {
  const digest = Buffer.from('7509e5bda0c762d2bac7f90d758b5b2263fa01ccbc542ab5e3df163be08e6ca9', 'hex');
  const digestInfo = Buffer.concat([
    Buffer.from('302f300b06096086480165030402010420', 'hex'),
    digest
  ]);
  assert.equal(verifyEncodedDigestInfo(digestInfo, 'hello world!'), true);
});

test('rejects bytes after canonical DigestInfo when the parser option is disabled', () => {
  const digest = Buffer.from('7509e5bda0c762d2bac7f90d758b5b2263fa01ccbc542ab5e3df163be08e6ca9', 'hex');
  const digestInfo = Buffer.concat([
    Buffer.from('3031300d060960864801650304020105000420', 'hex'),
    digest,
    Buffer.from([0xff])
  ]);
  assert.throws(
    () => verifyEncodedDigestInfo(digestInfo, 'hello world!', {_parseAllDigestBytes: false}),
    /valid RSASSA-PKCS1-v1_5 DigestInfo/
  );
});
