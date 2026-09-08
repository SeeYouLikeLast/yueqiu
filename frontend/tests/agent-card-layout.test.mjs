import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const css = readFileSync(new URL('../src/styles.css', import.meta.url), 'utf8')
const app = readFileSync(new URL('../src/App.vue', import.meta.url), 'utf8')

function declarations(selector) {
  const escaped = selector.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  const match = css.match(new RegExp(`${escaped}\\s*\\{([^}]*)\\}`))
  assert.ok(match, `missing CSS rule for ${selector}`)
  return match[1].replace(/\s+/g, ' ')
}

test('agent place cards keep their own height in the horizontal carousel', () => {
  assert.match(declarations('.agent-card-carousel'), /align-items:\s*flex-start\s*;/)
  assert.match(declarations('.agent-place-card'), /align-content:\s*start\s*;/)
})

test('long agent place titles are limited to two lines', () => {
  const rule = declarations('.agent-place-main h3')
  assert.match(rule, /display:\s*-webkit-box\s*;/)
  assert.match(rule, /-webkit-line-clamp:\s*2\s*;/)
  assert.match(rule, /-webkit-box-orient:\s*vertical\s*;/)
  assert.match(rule, /overflow:\s*hidden\s*;/)
})

test('agent place cards always render the same generated cover fallback as the place list', () => {
  assert.match(app, /<img\s+:src="agentPlaceCover\(bundle\.place\)"/)
  assert.match(app, /@error="agentPlaceImageFallback\(\$event, bundle\.place\)"/)
  assert.match(app, /function agentPlaceCover\(card: AgentCard\)[\s\S]*?placeListCover\(placeFromAgentCard\(card\)\)/)
  assert.doesNotMatch(app, /<img\s+v-if="bundle\.place\.coverUrl"/)
})
