import { computed, ref } from 'vue'
import { demoAssistantCard } from '../data/demo'
import { aiWs } from '../services/api'

const CARD_TYPES = new Set(['itinerary', 'service_recommendation', 'knowledge_answer', 'clarifying_question', 'pending_booking', 'error'])
const threadId = `web-${crypto.randomUUID()}`
const messages = ref([])
const phases = ref(makePhases())
const activeCard = ref(null)
const previousCard = ref(null)
const mode = ref('idle')
const requestSeq = ref(0)
let activeSocket = null

function makePhases() { return ['需求理解', '知识检索', '服务协同', '行程生成'].map(label => ({ label, state: 'idle' })) }
function isCurrent(seq, socket) { return seq === requestSeq.value && socket === activeSocket }
function validServiceId(value) { return typeof value === 'string' && value.trim() }
function safeCard(raw) {
  if (!CARD_TYPES.has(raw?.type)) return { type: 'error', title: '内容暂不可展示', summary: '向导返回了不支持的内容。', data: {}, sources: [] }
  return { type: raw.type, title: raw.title || '', summary: raw.summary || '', data: raw.data || {}, sources: Array.isArray(raw.sources) ? raw.sources.map(source => ({ title: source?.title || '' })) : [] }
}
function cardFingerprint(card) { return JSON.stringify({ type: card.type, title: card.title, summary: card.summary, data: card.data }) }
function close(socket = activeSocket) {
  if (socket && socket.readyState < WebSocket.CLOSING) socket.close()
  if (socket === activeSocket) activeSocket = null
}
function settle(seq, socket, outcome) {
  if (!isCurrent(seq, socket)) return
  phases.value = phases.value.map(phase => ({ ...phase, state: phase.state === 'active' ? (outcome === 'completed' ? 'done' : 'failed') : phase.state === 'idle' ? 'skipped' : phase.state }))
  mode.value = outcome === 'completed' ? 'idle' : 'error'
  close(socket)
}
function acceptCard(seq, socket, raw) {
  if (!isCurrent(seq, socket)) return
  const card = safeCard(raw)
  if (activeCard.value && cardFingerprint(activeCard.value) === cardFingerprint(card)) return
  previousCard.value = activeCard.value
  activeCard.value = card
  messages.value.push({ role: 'assistant', title: card.title, summary: card.summary })
}
function phaseIndex(event) {
  if (event.node === 'route' || event.node === 'intent') return 0
  if (event.node === 'retrieve' || event.tool === 'knowledge_retrieval') return 1
  if (event.node === 'service_search' || event.tool === 'service_search') return 2
  if (event.node === 'itinerary') return 3
  return -1
}
function applyEvent(seq, socket, event) {
  if (!isCurrent(seq, socket)) return
  if (event?.type === 'node_started') {
    const index = phaseIndex(event)
    if (index >= 0) { phases.value = phases.value.map((phase, i) => ({ ...phase, state: i === index ? 'active' : i < index && phase.state === 'active' ? 'done' : phase.state })) }
  }
  if (event?.type === 'tool_finished') {
    const index = phaseIndex(event)
    if (index >= 0) phases.value[index].state = 'done'
  }
  if (event?.type === 'card_ready') acceptCard(seq, socket, event.card || event.data)
  if (event?.type === 'completed' || event?.type === 'failed') settle(seq, socket, event.type)
}
function send(text) {
  const userText = text.trim()
  if (!userText) return
  const seq = ++requestSeq.value
  close(activeSocket)
  phases.value = makePhases()
  mode.value = 'live'
  messages.value.push({ role: 'user', text: userText })
  const socket = activeSocket = new WebSocket(aiWs)
  socket.onopen = () => { if (isCurrent(seq, socket)) socket.send(JSON.stringify({ thread_id: threadId, user_text: userText })) }
  socket.onmessage = ({ data }) => { try { applyEvent(seq, socket, JSON.parse(data)) } catch (_) {} }
  socket.onerror = () => { if (isCurrent(seq, socket)) { acceptCard(seq, socket, { type: 'error', title: '向导暂不可用', summary: '连接暂时中断，可选择查看演示结果。', data: { demoAvailable: true } }); settle(seq, socket, 'failed') } }
  socket.onclose = () => { if (isCurrent(seq, socket) && mode.value === 'live') { acceptCard(seq, socket, { type: 'error', title: '向导暂不可用', summary: '连接已关闭，可选择查看演示结果。', data: { demoAvailable: true } }); settle(seq, socket, 'failed') } }
}
function useDemo() {
  if (activeCard.value?.type !== 'error' || activeCard.value.data?.demoAvailable !== true) return
  const seq = ++requestSeq.value
  close(activeSocket)
  const demo = safeCard(demoAssistantCard)
  previousCard.value = activeCard.value
  activeCard.value = demo
  messages.value.push({ role: 'assistant', title: demo.title, summary: demo.summary })
  phases.value = phases.value.map(phase => ({ ...phase, state: 'done' }))
  mode.value = 'demo'
}
export function useAssistantSession() { return { threadId, messages, phases, activeCard, previousCard, mode, requestSeq, send, useDemo, close, validServiceId } }
