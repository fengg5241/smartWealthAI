<template>
  <div v-if="!tenantId" class="login-prompt"><p>Redirecting to login...</p></div>
  <div v-else class="app-shell">
    <header class="topbar">
      <h1>SmartRAG Agent</h1>
      <div class="topbar-right">
        <span class="connection" :class="{ connected: sseReady, disconnected: !sseReady }">
          {{ sseReady ? 'Live' : 'Offline' }}
        </span>
        <button class="btn-icon" @click="showSettings = true" title="Settings">&#9881;</button>
        <button class="btn-text" @click="logout">Logout</button>
      </div>
    </header>
    <div class="main-area">
      <ConversationList
        :conversations="conversations"
        :selectedId="selectedConv?.id"
        @select="selectConversation"
      />
      <ChatWindow
        v-if="selectedConv"
        :conversation="selectedConv"
        :messages="messages"
        :tenantId="tenantId"
        :agentName="agentName"
        @send="sendReply"
        @take-over="takeOver"
        @release="releaseConv"
        @close="closeConversation"
        @update-customer="updateCustomer"
      />
      <div v-else class="empty-state">Select a conversation to start</div>
    </div>
    <SettingsModal v-if="showSettings" :tenantId="tenantId" @close="showSettings = false" />
    <!-- Toast for alerts -->
    <div v-if="toast" class="toast" :class="toast.type">{{ toast.message }}</div>
  </div>
</template>

<script>
import ConversationList from './components/ConversationList.vue'
import ChatWindow from './components/ChatWindow.vue'
import SettingsModal from './components/SettingsModal.vue'

export default {
  name: 'App',
  components: { ConversationList, ChatWindow, SettingsModal },
  data() {
    return {
      tenantId: null,
      agentName: 'agent',
      conversations: [],
      selectedConv: null,
      messages: [],
      sseReady: false,
      showSettings: false,
      eventSource: null,
      toast: null
    }
  },
  mounted() {
    this.tenantId = this.getTenantId()
    if (!this.tenantId) {
      window.location.href = '/login.html'
      return
    }
    // Set agent name from localStorage or prompt
    this.agentName = localStorage.getItem('agentName') || 'agent'
    this.loadConversations()
    this.connectSSE()
  },
  beforeUnmount() {
    if (this.eventSource) this.eventSource.close()
  },
  methods: {
    getTenantId() {
      const params = new URLSearchParams(window.location.search)
      if (params.get('tenant')) return params.get('tenant')
      const stored = localStorage.getItem('tenantId')
      if (stored) return stored
      return null
    },

    showToast(message, type = 'info') {
      this.toast = { message, type }
      setTimeout(() => { this.toast = null }, 4000)
    },

    async loadConversations() {
      try {
        const res = await fetch('/api/conversations', { headers: this.apiHeaders() })
        if (res.ok) this.conversations = await res.json()
      } catch (e) { console.error('Failed to load conversations', e) }
    },

    async selectConversation(conv) {
      this.selectedConv = conv
      conv.unreadCount = 0
      try {
        const res = await fetch(`/api/conversations/${conv.id}/messages`, { headers: this.apiHeaders() })
        if (res.ok) {
          const data = await res.json()
          this.messages = data.messages.reverse()
        }
      } catch (e) { console.error('Failed to load messages', e) }
    },

    async sendReply(content) {
      if (!this.selectedConv) return
      try {
        const res = await fetch(`/api/conversations/${this.selectedConv.id}/reply`, {
          method: 'POST',
          headers: { ...this.apiHeaders(), 'Content-Type': 'application/json' },
          body: JSON.stringify({ content, agentName: this.agentName })
        })
        if (res.ok) {
          const msg = await res.json()
          this.messages.push(msg)
        } else if (res.status === 400) {
          const err = await res.json()
          if (err.error === 'OUTSIDE_24H_WINDOW') {
            this.showToast('Outside 24h window — please use a template message.', 'warn')
          }
        } else if (res.status === 403) {
          const err = await res.json()
          this.showToast(err.error || 'You cannot reply to this conversation.', 'error')
        }
      } catch (e) { console.error('Failed to send reply', e) }
    },

    async takeOver() {
      if (!this.selectedConv) return
      try {
        const res = await fetch(`/api/conversations/${this.selectedConv.id}/take-over`, {
          method: 'PUT',
          headers: { ...this.apiHeaders(), 'Content-Type': 'application/json' },
          body: JSON.stringify({ agentName: this.agentName })
        })
        if (res.ok) {
          this.selectedConv.status = 'human_assigned'
          this.selectedConv.assignedAgent = this.agentName
          this.loadConversations()
        } else if (res.status === 409) {
          const err = await res.json()
          this.showToast(err.message || 'This conversation has already been taken over by another agent.', 'warn')
          this.loadConversations()
          // Refresh selected conv
          const updated = this.conversations.find(c => c.id === this.selectedConv.id)
          if (updated) {
            this.selectedConv = { ...this.selectedConv, ...updated }
          }
        }
      } catch (e) { console.error('Failed to take over', e) }
    },

    async releaseConv() {
      if (!this.selectedConv) return
      try {
        const res = await fetch(`/api/conversations/${this.selectedConv.id}/release`, {
          method: 'PUT',
          headers: { ...this.apiHeaders(), 'Content-Type': 'application/json' },
          body: JSON.stringify({ agentName: this.agentName })
        })
        if (res.ok) {
          this.selectedConv.status = 'ai_active'
          this.selectedConv.assignedAgent = null
          this.loadConversations()
        } else if (res.status === 409) {
          this.showToast('Cannot release — status has changed.', 'warn')
        }
      } catch (e) { console.error('Failed to release', e) }
    },

    async closeConversation() {
      if (!this.selectedConv) return
      try {
        await fetch(`/api/conversations/${this.selectedConv.id}/close`, {
          method: 'PUT',
          headers: this.apiHeaders()
        })
        this.selectedConv = null
        this.messages = []
        this.loadConversations()
      } catch (e) { console.error('Failed to close conversation', e) }
    },

    async updateCustomer(fields) {
      if (!this.selectedConv) return
      try {
        const res = await fetch(`/api/conversations/${this.selectedConv.id}/customer`, {
          method: 'PUT',
          headers: { ...this.apiHeaders(), 'Content-Type': 'application/json' },
          body: JSON.stringify(fields)
        })
        if (res.ok && this.selectedConv) {
          for (const [k, v] of Object.entries(fields)) {
            this.selectedConv[k] = v
          }
          this.loadConversations()
        }
      } catch (e) { console.error('Failed to update customer', e) }
    },

    connectSSE() {
      const url = `/api/conversations/stream?tid=${encodeURIComponent(this.tenantId)}`
      this.eventSource = new EventSource(url)
      this.eventSource.addEventListener('connected', () => { this.sseReady = true })
      this.eventSource.onmessage = (event) => {
        try {
          const data = JSON.parse(event.data)
          this.handleSSEEvent(data)
        } catch (e) { console.error('SSE parse error', e) }
      }
      this.eventSource.onerror = () => { this.sseReady = false }
    },

    handleSSEEvent(data) {
      if (data.type === 'new_message') {
        const idx = this.conversations.findIndex(c => c.id === data.conversationId)
        const entry = {
          id: data.conversationId, customerId: data.customerId,
          customerPhone: data.customerPhone, customerName: data.customerName,
          status: data.conversationStatus, unreadCount: data.unreadCount,
          updatedAt: data.timestamp,
          lastMessage: data.content?.length > 80 ? data.content.substring(0, 80) + '...' : data.content,
          lastMessageTime: data.timestamp
        }
        if (idx >= 0) {
          this.conversations[idx] = { ...this.conversations[idx], ...entry }
        } else {
          this.conversations.unshift(entry)
        }
        if (this.selectedConv && this.selectedConv.id === data.conversationId) {
          this.messages.push({
            id: data.messageId, direction: data.direction,
            senderType: data.senderType, content: data.content, createdAt: data.timestamp
          })
        }
        this.conversations = [...this.conversations]
      } else if (data.type === 'take_over') {
        // Another agent took over — update the conversation locally
        const c = this.conversations.find(x => x.id === data.conversationId)
        if (c) {
          c.status = data.conversationStatus || 'human_assigned'
          c.assignedAgent = data.assignedAgent
          this.conversations = [...this.conversations]
          if (this.selectedConv && this.selectedConv.id === data.conversationId) {
            this.selectedConv.status = c.status
            this.selectedConv.assignedAgent = c.assignedAgent
          }
        }
      } else if (data.type === 'outside_24h') {
        this.showToast('This customer is outside the 24h window. Use a template to send a message.', 'warn')
      }
    },

    apiHeaders() {
      return { 'X-Tenant-ID': this.tenantId }
    },

    logout() {
      localStorage.removeItem('tenantId')
      window.location.href = '/login.html'
    }
  }
}
</script>

<style>
.login-prompt { display: flex; align-items: center; justify-content: center; height: 100vh; font-size: 18px; color: #666; }
.app-shell { display: flex; flex-direction: column; height: 100vh; }
.topbar { display: flex; align-items: center; justify-content: space-between; padding: 0 20px; height: 52px; background: #1a1a2e; color: #fff; flex-shrink: 0; }
.topbar h1 { font-size: 16px; font-weight: 600; }
.topbar-right { display: flex; align-items: center; gap: 12px; }
.connection { font-size: 11px; padding: 2px 8px; border-radius: 10px; }
.connection.connected { background: #2ecc71; color: #fff; }
.connection.disconnected { background: #e74c3c; color: #fff; }
.btn-icon { background: none; border: none; color: #fff; font-size: 16px; cursor: pointer; padding: 4px 8px; }
.btn-text { background: none; border: 1px solid rgba(255,255,255,0.3); color: #fff; padding: 4px 12px; border-radius: 4px; cursor: pointer; font-size: 12px; }
.main-area { display: flex; flex: 1; overflow: hidden; }
.empty-state { flex: 1; display: flex; align-items: center; justify-content: center; color: #999; font-size: 16px; }
.toast { position: fixed; bottom: 20px; right: 20px; padding: 12px 20px; border-radius: 6px; font-size: 13px; z-index: 2000; max-width: 400px; }
.toast.info { background: #1a1a2e; color: #fff; }
.toast.warn { background: #f39c12; color: #fff; }
.toast.error { background: #e74c3c; color: #fff; }
</style>
