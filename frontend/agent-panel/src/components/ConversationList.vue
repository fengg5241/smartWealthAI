<template>
  <aside class="sidebar">
    <div class="search-box">
      <input v-model="search" type="text" placeholder="Search conversations..." />
    </div>
    <div class="conv-list">
      <template v-for="group in groupedConversations" :key="group.label">
        <div class="group-label">{{ group.label }} ({{ group.items.length }})</div>
        <div
          v-for="conv in group.items"
          :key="conv.id"
          class="conv-item"
          :class="{ selected: selectedId === conv.id }"
          @click="$emit('select', conv)"
        >
          <div class="conv-top">
            <span class="status-dot" :class="conv.status"></span>
            <span class="conv-name">{{ conv.customerName || conv.customerPhone }}</span>
            <span v-if="conv.unreadCount > 0" class="badge">{{ conv.unreadCount }}</span>
          </div>
          <div class="conv-preview">{{ conv.lastMessage || 'No messages' }}</div>
          <div class="conv-time">{{ formatTime(conv.lastMessageTime) }}</div>
        </div>
      </template>
      <div v-if="filtered.length === 0" class="no-results">No conversations</div>
    </div>
  </aside>
</template>

<script>
export default {
  name: 'ConversationList',
  props: {
    conversations: { type: Array, default: () => [] },
    selectedId: Number
  },
  emits: ['select'],
  data() {
    return { search: '' }
  },
  computed: {
    filtered() {
      if (!this.search.trim()) return this.conversations
      const q = this.search.toLowerCase()
      return this.conversations.filter(c =>
        (c.customerName || '').toLowerCase().includes(q) ||
        (c.customerPhone || '').includes(q) ||
        (c.lastMessage || '').toLowerCase().includes(q)
      )
    },
    groupedConversations() {
      const groups = [
        { label: 'Active', items: [] },
        { label: 'Taken Over', items: [] },
        { label: 'Needs Human', items: [] },
        { label: 'Human', items: [] }
      ]
      for (const c of this.filtered) {
        if (c.status === 'pending_human') groups[1].items.push(c)
        else if (c.status === 'human_assigned') groups[2].items.push(c)
        else groups[0].items.push(c)
      }
      return groups.filter(g => g.items.length > 0)
    }
  },
  methods: {
    formatTime(t) {
      if (!t) return ''
      const d = new Date(t)
      const now = new Date()
      if (d.toDateString() === now.toDateString()) {
        return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
      }
      return d.toLocaleDateString([], { month: 'short', day: 'numeric' })
    }
  }
}
</script>

<style scoped>
.sidebar {
  width: 300px; min-width: 300px;
  background: #fff; border-right: 1px solid #e0e0e0;
  display: flex; flex-direction: column; overflow: hidden;
}
.search-box { padding: 10px; }
.search-box input {
  width: 100%; padding: 8px 12px; border: 1px solid #ddd; border-radius: 6px;
  font-size: 13px; outline: none;
}
.search-box input:focus { border-color: #4a90d9; }
.conv-list { flex: 1; overflow-y: auto; }
.group-label {
  padding: 6px 12px; font-size: 11px; font-weight: 600;
  color: #888; text-transform: uppercase; background: #fafafa;
  border-bottom: 1px solid #eee; border-top: 1px solid #eee;
}
.conv-item {
  padding: 12px; border-bottom: 1px solid #f0f0f0; cursor: pointer;
  transition: background 0.15s;
}
.conv-item:hover { background: #f5f7fa; }
.conv-item.selected { background: #e8f0fe; }
.conv-top { display: flex; align-items: center; gap: 8px; margin-bottom: 4px; }
.status-dot { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; }
.status-dot.ai_active { background: #2ecc71; }
.status-dot.pending_human { background: #f39c12; }
.status-dot.human_assigned { background: #3498db; }
.conv-name { font-size: 14px; font-weight: 500; flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.badge { background: #e74c3c; color: #fff; font-size: 11px; padding: 1px 6px; border-radius: 10px; }
.conv-preview { font-size: 12px; color: #888; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.conv-time { font-size: 11px; color: #aaa; margin-top: 2px; text-align: right; }
.no-results { padding: 20px; text-align: center; color: #999; font-size: 13px; }
</style>
