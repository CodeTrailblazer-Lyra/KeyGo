/* fetch-api.js — KeyGo 前端 API 封装层 */

const API = {
    // ---------- 激活码 ----------

    /** 获取列表（分页） */
    async listCodes({ status, keyword, page = 1, size = 20 }) {
        const params = new URLSearchParams();
        if (status) params.set('status', status);
        if (keyword) params.set('keyword', keyword);
        params.set('page', page);
        params.set('size', size);
        const resp = await fetch('/admin/code/api/list?' + params);
        return resp.ok ? resp.json() : null;
    },

    /** 单个获取 */
    async claimCode() {
        const resp = await fetch('/admin/code/claim-json', { method: 'POST' });
        return resp.ok ? resp.json() : null;
    },

    /** 批量获取 */
    async batchClaim(count) {
        const resp = await fetch('/admin/code/api/batch-claim', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ count })
        });
        return resp.ok ? resp.json() : null;
    },

    /** 标记已使用 */
    async markUsed(id) {
        const resp = await fetch('/admin/code/api/use/' + id, { method: 'POST' });
        return resp.ok ? resp.json() : null;
    },

    /** 标记未使用 */
    async markUnused(id) {
        const resp = await fetch('/admin/code/api/unuse/' + id, { method: 'POST' });
        return resp.ok ? resp.json() : null;
    },

    /** 批量标记已使用 */
    async batchUse(ids) {
        const resp = await fetch('/admin/code/api/batch-use', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(ids)
        });
        return resp.ok ? resp.json() : null;
    },

    /** 批量恢复未使用 */
    async batchUnuse(ids) {
        const resp = await fetch('/admin/code/api/batch-unuse', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(ids)
        });
        return resp.ok ? resp.json() : null;
    },

    /** 删除单个 */
    async deleteCode(id) {
        const resp = await fetch('/admin/code/api/code/' + id, { method: 'DELETE' });
        return resp.ok ? resp.json() : null;
    },

    /** 批量删除 */
    async batchDelete(ids) {
        const resp = await fetch('/admin/code/api/batch-delete', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(ids)
        });
        return resp.ok ? resp.json() : null;
    },

    /** 导入 Excel */
    async importExcel(file) {
        const formData = new FormData();
        formData.append('file', file);
        const resp = await fetch('/admin/code/api/import', { method: 'POST', body: formData });
        return resp.ok ? resp.json() : null;
    },

    /** 更新备注 */
    async updateRemark(id, remark) {
        const resp = await fetch('/admin/code/api/remark/' + id, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ remark })
        });
        return resp.ok ? resp.json() : null;
    },

    /** 我的获取记录 */
    async myCodes() {
        const resp = await fetch('/admin/code/api/my-codes');
        return resp.ok ? resp.json() : null;
    },

    /** 导出 Excel（触发下载） */
    exportUrl(status, keyword) {
        const params = new URLSearchParams();
        if (status) params.set('status', status);
        if (keyword) params.set('keyword', keyword);
        return '/admin/code/api/export?' + params;
    },

    // ---------- 用户 ----------

    async userInfo() {
        const resp = await fetch('/admin/api/me');
        return resp.ok ? resp.json() : null;
    },

    async pendingUsers() {
        const resp = await fetch('/admin/api/users/pending');
        return resp.ok ? resp.json() : [];
    },

    async allUsers() {
        const resp = await fetch('/admin/api/users');
        return resp.ok ? resp.json() : [];
    },

    async approveUser(id) {
        const resp = await fetch('/admin/api/users/' + id + '/approve', { method: 'POST' });
        return resp.ok ? resp.json() : null;
    },

    async rejectUser(id) {
        const resp = await fetch('/admin/api/users/' + id + '/reject', { method: 'POST' });
        return resp.ok ? resp.json() : null;
    },

    async changeRole(id, role) {
        const params = new URLSearchParams();
        params.set('role', role);
        await fetch('/admin/api/users/' + id + '/role', { method: 'POST', body: params });
    },

    async deleteUser(id) {
        const resp = await fetch('/admin/api/users/' + id, { method: 'DELETE' });
        return resp.ok ? resp.json() : null;
    },

    async changePassword(payload) {
        const resp = await fetch('/admin/api/password', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        return resp.ok ? resp.json() : null;
    },

    // ---------- 操作日志 ----------

    async logs(page = 1, size = 20) {
        const resp = await fetch('/admin/code/api/logs?page=' + page + '&size=' + size);
        return resp.ok ? resp.json() : null;
    },

    // ---------- 公告 ----------

    async announcements() {
        const resp = await fetch('/admin/code/api/announcements');
        return resp.ok ? resp.json() : [];
    },

    async publishAnnouncement(content, pinned) {
        const resp = await fetch('/admin/code/api/announcements', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ content, pinned })
        });
        return resp.ok ? resp.json() : null;
    },

    async deleteAnnouncement(id) {
        const resp = await fetch('/admin/code/api/announcements/' + id, { method: 'DELETE' });
        return resp.ok ? resp.json() : null;
    },
};
