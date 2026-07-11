<template>
  <main class="chat-window">
    <!-- Header -->
    <div class="chat-header">
      <div class="chat-header-left">
        <span class="customer-name">{{ conversation.customerName || conversation.customerPhone }}</span>
        <span class="customer-phone">{{ conversation.customerPhone }}</span>
      </div>
      <div class="chat-header-right">
        <span class="status-tag" :class="conversation.status">{{ statusLabel }}</span>
        <span v-if="conversation.assignedAgent" class="assigned-badge">@{{ conversation.assignedAgent }}</span>
        <button v-if="canTakeOver" class="btn-action take-over" @click="$emit('take-over')">Take Over</button>
        <button v-if="canRelease" class="btn-action release" @click="$emit('release')">Release</button>
        <button v-if="conversation.status !== 'closed'" class="btn-action close" @click="$emit('close')">Close</button>
      </div>
    </div>

    <!-- Messages -->
    <div class="chat-messages" ref="msgContainer">
      <div v-for="msg in messages" :key="msg.id" class="msg-row" :class="msg.senderType">
        <div class="msg-bubble" :class="msg.senderType">
          <div class="msg-meta">
            <span class="msg-sender">{{ senderLabel(msg.senderType) }}</span>
            <span class="msg-time">{{ formatTime(msg.createdAt) }}</span>
          </div>
          <div class="msg-content">{{ msg.content }}</div>
        </div>
      </div>
      <div v-if="messages.length === 0" class="no-messages">No messages yet</div>
    </div>

    <!-- Customer profile panel -->
    <div class="customer-panel" v-if="showProfile">
      <h4>Customer Profile</h4>
      <label>Tags</label>
      <input v-model="editTags" @blur="saveCustomer" placeholder="e.g. high-intent, insurance" />
      <label>Budget</label>
      <input v-model="editBudget" @blur="saveCustomer" placeholder="e.g. $5000/year" />
      <label>Requirement</label>
      <textarea v-model="editRequirement" @blur="saveCustomer" placeholder="Customer needs..." rows="3"></textarea>
    </div>

    <!-- Input -->
    <div class="chat-input" v-if="conversation.status !== 'closed'">
      <button class="profile-toggle" @click="showProfile = !showProfile" :class="{ active: showProfile }">Profile</button>
      <input
        v-model="draft" type="text"
        :placeholder="inputPlaceholder"
        :disabled="!canReply"
        @keydown.enter="send"
      />
      <button class="send-btn" @click="send" :disabled="!canReply || !draft.trim()">Send</button>
    </div>
    <div v-else class="chat-input closed-banner">This conversation has been closed.</div>
  </main>
</template>

<script>
export default {
  name: 'ChatWindow',
  props: {
    conversation: Object,
    messages: { type: Array, default: () => [] },
    tenantId: String,
    agentName: { type: String, default: 'agent' }
  },
  emits: ['send', 'take-over', 'release', 'close', 'update-customer'],
  data() {
    return {
      draft: '',
      showProfile: false,
      editTags: '',
      editBudget: '',
      editRequirement: ''
    }
  },
  computed: {
    statusLabel() {
      const map = { ai_active: 'AI Active', pending_human: 'Needs Human', human_assigned: 'Human', closed: 'Closed' }
      return map[this.conversation?.status] || this.conversation?.status
    },
    canTakeOver() {
      const s = this.conversation?.status
      return s === 'ai_active' || s === 'pending_human'
    },
    canRelease() {
      return this.conversation?.status === 'human_assigned'
          && this.conversation?.assignedAgent === this.agentName
    },
    canReply() {
      const s = this.conversation?.status
      if (s === 'ai_active' || s === 'pending_human') return false
      if (s === 'human_assigned') {
        return this.conversation?.assignedAgent === this.agentName
      }
      return false
    },
    inputPlaceholder() {
      const s = this.conversation?.status
      if (s === 'ai_active') return 'AI is handling this. Take over to reply manually.'
      if (s === 'pending_human') return 'This conversation needs a human. Take over first.'
      if (s === 'human_assigned') {
        if (this.conversation?.assignedAgent !== this.agentName) {
          return 'Another agent is handling this conversation.'
        }
        return 'Type your reply...'
      }
      return 'Cannot reply'
    }
  },
  watch: {
    conversation: {
      immediate: true,
      handler(c) {
        if (c) {
          this.editTags = c.tags || ''
          this.editBudget = c.budget || ''
          this.editRequirement = c.requirement || ''
        }
      }
    }
  },
  updated() {
    this.scrollToBottom()
  },
  methods: {
    send() {
      const content = this.draft.trim()
      if (!content) return
      this.$emit('send', content)
      this.draft = ''
    },
    saveCustomer() {
      this.$emit('update-customer', {
        tags: this.editTags, budget: this.editBudget, requirement: this.editRequirement
      })
    },
    senderLabel(type) {
      const map = { customer: 'Customer', ai: 'AI', agent: 'You' }
      return map[type] || type
    },
    formatTime(t) {
      if (!t) return ''
      return new Date(t).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
    },
    scrollToBottom() {
      const el = this.$refs.msgContainer
      if (el) el.scrollTop = el.scrollHeight
    }
  }
}
</script>

<style scoped>
.chat-window { flex: 1; display: flex; flex-direction: column; background: #fff; }
.chat-header { display: flex; justify-content: space-between; align-items: center; padding: 12px 16px; border-bottom: 1px solid #eee; flex-shrink: 0; }
.chat-header-left { display: flex; flex-direction: column; }
.customer-name { font-size: 15px; font-weight: 600; }
.customer-phone { font-size: 12px; color: #888; }
.chat-header-right { display: flex; align-items: center; gap: 8px; }
.status-tag { font-size: 11px; padding: 2px 8px; border-radius: 10px; }
.status-tag.ai_active { background: #d5f5e3; color: #27ae60; }
.status-tag.pending_human { background: #fdebd0; color: #e67e22; }
.status-tag.human_assigned { background: #d6eaf8; color: #2980b9; }
.status-tag.closed { background: #f2f3f4; color: #999; }
.assigned-badge { font-size: 11px; color: #2980b9; font-weight: 500; }
.btn-action { padding: 4px 12px; border: none; border-radius: 4px; font-size: 12px; cursor: pointer; }
.btn-action.take-over { background: #e67e22; color: #fff; }
.btn-action.release { background: #f0f0f0; color: #555; }
.btn-action.close { background: #e0e0e0; color: #666; }

.chat-messages { flex: 1; overflow-y: auto; padding: 16px; }
.msg-row { margin-bottom: 12px; display: flex; }
.msg-row.customer { justify-content: flex-start; }
.msg-row.ai, .msg-row.agent { justify-content: flex-end; }
.msg-bubble { max-width: 70%; padding: 10px 14px; border-radius: 12px; }
.msg-bubble.customer { background: #f0f0f0; border-bottom-left-radius: 4px; }
.msg-bubble.ai { background: #d5f5e3; border-bottom-right-radius: 4px; }
.msg-bubble.agent { background: #d6eaf8; border-bottom-right-radius: 4px; }
.msg-meta { display: flex; justify-content: space-between; margin-bottom: 4px; }
.msg-sender { font-size: 11px; font-weight: 600; color: #666; }
.msg-time { font-size: 10px; color: #aaa; }
.msg-content { font-size: 14px; line-height: 1.5; white-space: pre-wrap; word-break: break-word; }
.no-messages { text-align: center; color: #ccc; padding: 40px; }

.customer-panel { padding: 12px 16px; border-top: 1px solid #eee; background: #fafafa; }
.customer-panel h4 { font-size: 13px; margin-bottom: 8px; }
.customer-panel label { display: block; font-size: 11px; color: #888; margin-top: 6px; }
.customer-panel input, .customer-panel textarea { width: 100%; padding: 6px 8px; border: 1px solid #ddd; border-radius: 4px; font-size: 13px; margin-top: 2px; outline: none; }
.customer-panel input:focus, .customer-panel textarea:focus { border-color: #4a90d9; }

.chat-input { display: flex; gap: 8px; padding: 12px 16px; border-top: 1px solid #eee; align-items: center; flex-shrink: 0; }
.chat-input input { flex: 1; padding: 8px 12px; border: 1px solid #ddd; border-radius: 6px; font-size: 14px; outline: none; }
.chat-input input:focus { border-color: #4a90d9; }
.chat-input input:disabled { background: #f5f5f5; color: #999; }
.send-btn { padding: 8px 20px; background: #1a1a2e; color: #fff; border: none; border-radius: 6px; font-size: 13px; cursor: pointer; }
.send-btn:disabled { opacity: 0.4; cursor: default; }
.profile-toggle { padding: 8px 12px; background: #f0f0f0; border: 1px solid #ddd; border-radius: 6px; font-size: 12px; cursor: pointer; }
.profile-toggle.active { background: #1a1a2e; color: #fff; border-color: #1a1a2e; }
.closed-banner { padding: 16px; text-align: center; color: #999; font-size: 13px; border-top: 1px solid #eee; flex-shrink: 0; }
</style>
