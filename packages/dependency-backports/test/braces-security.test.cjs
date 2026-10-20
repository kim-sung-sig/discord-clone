'use strict';

const assert = require('node:assert/strict');
const test = require('node:test');
const braces = require('braces');

test('rejects deeply nested brace and parenthesis patterns before recursive walks', () => {
  assert.doesNotThrow(() => braces('{'.repeat(100) + 'a,b' + '}'.repeat(100)));
  assert.throws(() => braces('{'.repeat(101) + 'a,b' + '}'.repeat(101)), /exceeds max depth/);
  assert.doesNotThrow(() => braces('('.repeat(100) + 'a' + ')'.repeat(100)));
  assert.throws(() => braces('('.repeat(101) + 'a' + ')'.repeat(101)), /exceeds max depth/);
});

test('applies a lower caller-supplied depth limit and leaves escaped braces alone', () => {
  assert.throws(() => braces('{{a,b},c}', { maxDepth: 1 }), /exceeds max depth/);
  assert.doesNotThrow(() => braces('\\{'.repeat(101) + 'a' + '\\}'.repeat(101)));
});

test('preserves regular nested expansion and stringify behavior', () => {
  assert.deepEqual(braces.expand('{a,{b,c}}'), ['a', 'b', 'c']);
  const ast = braces.parse('{{a}}');
  assert.equal(braces.stringify(ast, { escapeInvalid: true }), '{{a}}');
});

test('bounds directly supplied recursive ASTs', () => {
  let ast = { type: 'text', value: 'a' };
  for (let i = 0; i < 101; i++) ast = { type: 'brace', nodes: [ast] };
  ast = { type: 'root', nodes: [ast] };
  assert.throws(() => braces.compile(ast), /exceeds max depth/);
  assert.throws(() => braces.expand(ast), /exceeds max depth/);
  assert.throws(() => braces.stringify(ast), /exceeds max depth/);
});

test('rejects a cyclic AST parent chain instead of looping', () => {
  const ast = { type: 'paren', nodes: [{ type: 'text', value: 'a' }] };
  ast.parent = ast;
  assert.throws(() => braces.expand(ast), /parent chain contains a cycle/);
});
