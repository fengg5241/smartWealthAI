<template>
  <div class="modal-overlay" @click.self="$emit('close')">
    <div class="modal">
      <div class="modal-header">
        <h3>Settings</h3>
        <button class="close-btn" @click="$emit('close')">&times;</button>
      </div>
      <div class="modal-body">
        <div class="setting-group">
          <h4>WhatsApp Number</h4>
          <p class="hint">Your WhatsApp Business number is linked via the admin panel.</p>
        </div>
        <div class="setting-group">
          <h4>Notifications</h4>
          <label>Email for alerts</label>
          <input v-model="email" type="email" placeholder="agent@example.com" />
          <label>WhatsApp for alerts</label>
          <input v-model="waPhone" type="text" placeholder="+6598765432" />
        </div>
        <div class="setting-group">
          <h4>Auto Reply</h4>
          <label>
            <input type="checkbox" v-model="autoReplyEnabled" />
            Enable auto-reply outside business hours
          </label>
          <textarea v-if="autoReplyEnabled" v-model="autoReplyText" rows="3"
            placeholder="Sorry, we're currently closed. We'll get back to you during business hours."></textarea>
        </div>
      </div>
      <div class="modal-footer">
        <button class="save-btn" @click="saveSettings">Save</button>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'SettingsModal',
  props: { tenantId: String },
  emits: ['close'],
  data() {
    return {
      email: localStorage.getItem('agentEmail') || '',
      waPhone: localStorage.getItem('agentWaPhone') || '',
      autoReplyEnabled: localStorage.getItem('autoReplyEnabled') === 'true',
      autoReplyText: localStorage.getItem('autoReplyText') || ''
    }
  },
  methods: {
    saveSettings() {
      localStorage.setItem('agentEmail', this.email)
      localStorage.setItem('agentWaPhone', this.waPhone)
      localStorage.setItem('autoReplyEnabled', this.autoReplyEnabled)
      localStorage.setItem('autoReplyText', this.autoReplyText)
      this.$emit('close')
    }
  }
}
</script>

<style scoped>
.modal-overlay {
  position: fixed; top: 0; left: 0; width: 100%; height: 100%;
  background: rgba(0,0,0,0.4); display: flex; align-items: center;
  justify-content: center; z-index: 1000;
}
.modal {
  background: #fff; border-radius: 8px; width: 440px; max-height: 80vh;
  overflow-y: auto; box-shadow: 0 4px 24px rgba(0,0,0,0.15);
}
.modal-header {
  display: flex; justify-content: space-between; align-items: center;
  padding: 16px 20px; border-bottom: 1px solid #eee;
}
.modal-header h3 { font-size: 16px; }
.close-btn { background: none; border: none; font-size: 20px; cursor: pointer; color: #888; }
.modal-body { padding: 20px; }
.setting-group { margin-bottom: 20px; }
.setting-group h4 { font-size: 14px; margin-bottom: 8px; }
.hint { font-size: 12px; color: #888; }
.setting-group label { display: block; font-size: 12px; color: #666; margin-top: 8px; margin-bottom: 4px; }
.setting-group input[type="email"],
.setting-group input[type="text"],
.setting-group textarea {
  width: 100%; padding: 6px 10px; border: 1px solid #ddd; border-radius: 4px; font-size: 13px; outline: none;
}
.setting-group textarea { resize: vertical; }
.setting-group input:focus, .setting-group textarea:focus { border-color: #4a90d9; }
.modal-footer { padding: 12px 20px; border-top: 1px solid #eee; text-align: right; }
.save-btn { padding: 8px 24px; background: #1a1a2e; color: #fff; border: none; border-radius: 6px; font-size: 13px; cursor: pointer; }
</style>
