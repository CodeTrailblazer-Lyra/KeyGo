/* fetch-app.js — KeyGo Vue 应用逻辑 */

/* global Vue, API, INIT */

const { createApp } = Vue;

createApp({
    data() {
        return {
            menuOpen: false,
            view: 'fetch',
            // 用户
            username: INIT.username,
            role: '',
            isAdmin: false,
            isSuperAdmin: false,
            // stats
            stats: { total: INIT.total, available: INIT.available, used: INIT.used },
            // 获取弹窗
            showClaimModal: false, claimDone: false, claiming: false,
            claimOk: false, claimCode: '', claimError: '',
            // 批量获取
            showBatchClaimModal: false,
            batchClaimCount: 1,
            batchClaimDone: false,
            batchClaiming: false,
            batchClaimResult: [],
            // 查询
            codes: [], listFilter: '', listKeyword: '', listStats: { total: 0, available: 0, used: 0 },
            listPage: 1, listSize: 20, listTotalPages: 1, listTotalElements: 0,
            listLoading: false,
            // 标记
            showMarkModal: false, markId: null,
            showUnmarkModal: false, unmarkId: null,
            // 用户管理
            pendingUsers: [], allUsers: [],
            pendingCount: 0,
            toasts: [],
            pollTimer: null,
            notifiedPending: false,
            // 导入
            showImportModal: false,
            importing: false,
            importResult: '',
            importOk: false,
            importFile: null,
            dragOver: false,
            // 批量标记
            selectedIds: [],
            // 批量删除
            showBatchDeleteModal: false,
            // 删除用户
            showDeleteModal: false,
            deleteTarget: null,
            // 密码修改
            showPasswordModal: false,
            pwdTarget: null,
            pwdIsSelf: true,
            pwdOld: '',
            pwdNew: '',
            pwdNew2: '',
            pwdMatch: true,
            pwdResult: '',
            pwdResultOk: false,
            pwdLoading: false,
            // 我的获取记录
            myCodes: [],
            myCodesLoading: false,
            // 操作日志
            logs: [],
            logPage: 1,
            logTotalPages: 1,
            logTotalElements: 0,
            logsLoading: false,
            // 公告
            announcements: [],
            showAnnounceModal: false,
            announceContent: '',
            announcePinned: false,
            announcePublishing: false,
            // 备注编辑（inline）
            editingRemarkId: null,
            editingRemarkText: '',
            // 搜索防抖
            searchTimer: null,
            // 全局加载
            pageLoading: false,
        };
    },
    computed: {
        claimTitle() {
            if (!this.claimDone) return '确认获取';
            return this.claimOk ? '获取成功' : '获取失败';
        },
        allSelected() {
            return this.codes.length > 0 && this.selectedIds.length === this.codes.length;
        },
        pinnedAnnouncements() {
            return this.announcements.filter(a => a.pinned);
        },
        normalAnnouncements() {
            return this.announcements.filter(a => !a.pinned);
        },
        actionLabel() {
            const map = {
                'CLAIM': '获取', 'BATCH_CLAIM': '批量获取',
                'IMPORT': '导入', 'DELETE': '删除',
                'BATCH_DELETE': '批量删除',
                'MARK_USED': '标记已用', 'BATCH_MARK_USED': '批量标记',
                'MARK_UNUSED': '标记恢复', 'BATCH_MARK_UNUSED': '批量恢复',
                'PUBLISH_ANNOUNCEMENT': '发布公告',
            };
            return (action) => map[action] || action;
        }
    },
    mounted() {
        this.loadUserInfo();
        this.loadAnnouncements();
        this.pollTimer = setInterval(() => { this.checkPending(); }, 30000);
    },
    beforeUnmount() {
        if (this.pollTimer) clearInterval(this.pollTimer);
        if (this.searchTimer) clearTimeout(this.searchTimer);
    },
    methods: {
        // ----- 用户信息 -----
        async loadUserInfo() {
            try {
                const data = await API.userInfo();
                if (!data) return;
                this.role = data.role || '';
                this.isSuperAdmin = data.role === 'ROLE_SUPER_ADMIN';
                this.isAdmin = data.role === 'ROLE_ADMIN' || this.isSuperAdmin;
                if (this.isAdmin) this.checkPending();
            } catch { /* ignore */ }
        },

        // ----- 待审核轮询 -----
        async checkPending() {
            try {
                const list = await API.pendingUsers();
                this.pendingCount = list.length;
                if (this.pendingCount > 0 && !this.notifiedPending) {
                    this.notifiedPending = true;
                    this.addToast({ title: '待审核用户', desc: '有 ' + this.pendingCount + ' 个新注册用户等待审核', view: 'admin' });
                }
            } catch { /* ignore */ }
        },

        // ----- 通知 -----
        addToast(t) {
            this.toasts.push(t);
            setTimeout(() => {
                var idx = this.toasts.indexOf(t);
                if (idx > -1) this.toasts.splice(idx, 1);
            }, 5000);
        },
        removeToast(i) { this.toasts.splice(i, 1); },
        goToast(t) { if (t.view) this.goView(t.view); },

        // ----- 视图切换 -----
        goView(v) {
            this.view = v;
            this.menuOpen = false;
            if (v === 'list') this.loadCodes('', 1);
            if (v === 'mycodes') this.loadMyCodes();
            if (v === 'logs') this.loadLogs(1);
            if (v === 'admin') { this.loadUsers(); this.notifiedPending = false; }
        },

        // ----- 获取 -----
        async doClaim() {
            this.claiming = true;
            try {
                const data = await API.claimCode();
                this.claimDone = true;
                this.claimOk = data.success;
                if (data.success) this.claimCode = data.code;
                else this.claimError = data.error;
                this.stats = { total: data.total, available: data.available, used: data.used };
            } catch (err) {
                this.claimDone = true; this.claimOk = false;
                this.claimError = err.message || '网络异常';
            } finally { this.claiming = false; }
        },
        closeClaimModal() {
            this.showClaimModal = false;
            setTimeout(() => { this.claimDone = false; this.claiming = false; }, 300);
        },

        // ----- 批量获取 -----
        openBatchClaim() {
            if (this.listStats.available === 0) {
                this.addToast({ title: '提示', desc: '暂无可用激活码' });
                return;
            }
            this.showBatchClaimModal = true;
        },
        async doBatchClaim() {
            var n = this.batchClaimCount;
            if (!n || n < 1 || n > 100) return;
            this.batchClaiming = true;
            try {
                const data = await API.batchClaim(n);
                if (data && data.success) {
                    this.batchClaimDone = true;
                    this.batchClaimResult = data.codes || [];
                    this.listStats.available = data.available;
                    this.listStats.total = data.total;
                    this.listStats.used = data.used;
                    this.stats = { total: data.total, available: data.available, used: data.used };
                }
            } catch { /* ignore */ }
            this.batchClaiming = false;
        },
        closeBatchClaim() {
            this.showBatchClaimModal = false;
            this.loadCodes(this.listFilter);
            setTimeout(() => { this.batchClaimDone = false; this.batchClaimResult = []; }, 300);
        },
        copyBatchResult() {
            this.copyToClipboard(this.batchClaimResult.join('\n'));
            this.addToast({ title: '复制成功', desc: '已复制 ' + this.batchClaimResult.length + ' 个激活码' });
        },

        // ----- 查询 -----
        async loadCodes(filter, page) {
            this.listFilter = filter || '';
            if (page !== undefined) this.listPage = page;
            this.listLoading = true;
            try {
                const data = await API.listCodes({
                    status: this.listFilter || undefined,
                    keyword: this.listKeyword || undefined,
                    page: this.listPage,
                    size: this.listSize
                });
                if (!data) return;
                this.codes = data.codes || [];
                this.listStats = { total: data.total, available: data.available, used: data.used };
                this.listTotalPages = data.totalPages || 1;
                this.listTotalElements = data.totalElements || 0;
                if (this.listPage > this.listTotalPages && this.listTotalPages > 0) {
                    this.listPage = this.listTotalPages;
                    this.loadCodes(this.listFilter);
                }
            } catch { /* ignore */ }
            this.listLoading = false;
        },
        debouncedSearch() {
            if (this.searchTimer) clearTimeout(this.searchTimer);
            this.searchTimer = setTimeout(() => { this.loadCodes(this.listFilter, 1); }, 400);
        },
        confirmMark(id) { this.markId = id; this.showMarkModal = true; },
        confirmUnmark(id) { this.unmarkId = id; this.showUnmarkModal = true; },
        async doMark() {
            if (!this.markId) return;
            try {
                const data = await API.markUsed(this.markId);
                if (data && data.success) {
                    this.addToast({ title: '标记成功', desc: data.message });
                    this.loadCodes(this.listFilter);
                }
            } catch { /* ignore */ }
            this.showMarkModal = false; this.markId = null;
        },
        async doUnmark() {
            if (!this.unmarkId) return;
            try {
                const data = await API.markUnused(this.unmarkId);
                if (data && data.success) {
                    this.addToast({ title: '恢复成功', desc: data.message });
                    this.loadCodes(this.listFilter);
                }
            } catch { /* ignore */ }
            this.showUnmarkModal = false; this.unmarkId = null;
        },

        // ----- 备注 -----
        startEditRemark(code) {
            this.editingRemarkId = code.id;
            this.editingRemarkText = code.remark || '';
        },
        cancelEditRemark() {
            this.editingRemarkId = null;
            this.editingRemarkText = '';
        },
        async saveRemark(id) {
            try {
                await API.updateRemark(id, this.editingRemarkText);
                this.editingRemarkId = null;
                this.editingRemarkText = '';
                this.loadCodes(this.listFilter);
            } catch { /* ignore */ }
        },

        // ----- 导出 -----
        exportExcel() {
            window.location.href = API.exportUrl(this.listFilter || undefined, this.listKeyword || undefined);
        },

        // ----- 我的获取记录 -----
        async loadMyCodes() {
            this.myCodesLoading = true;
            try {
                const data = await API.myCodes();
                this.myCodes = (data && data.codes) || [];
            } catch { /* ignore */ }
            this.myCodesLoading = false;
        },
        copyMyCode(code) {
            this.copyToClipboard(code);
            this.addToast({ title: '复制成功', desc: '激活码已复制到剪贴板' });
        },

        // ----- 操作日志 -----
        async loadLogs(page) {
            if (page !== undefined) this.logPage = page;
            this.logsLoading = true;
            try {
                const data = await API.logs(this.logPage, 20);
                if (!data) return;
                this.logs = data.logs || [];
                this.logTotalPages = data.totalPages || 1;
                this.logTotalElements = data.totalElements || 0;
            } catch { /* ignore */ }
            this.logsLoading = false;
        },

        // ----- 公告 -----
        async loadAnnouncements() {
            try {
                this.announcements = await API.announcements() || [];
            } catch { /* ignore */ }
        },
        async publishAnnouncement() {
            if (!this.announceContent.trim()) return;
            this.announcePublishing = true;
            try {
                const data = await API.publishAnnouncement(this.announceContent.trim(), this.announcePinned);
                if (data && data.success) {
                    this.announceContent = '';
                    this.announcePinned = false;
                    this.showAnnounceModal = false;
                    this.loadAnnouncements();
                    this.addToast({ title: '公告已发布' });
                }
            } catch { /* ignore */ }
            this.announcePublishing = false;
        },
        async deleteAnnouncement(id) {
            if (!confirm('确认删除该公告？')) return;
            try {
                const data = await API.deleteAnnouncement(id);
                if (data && data.success) {
                    this.loadAnnouncements();
                }
            } catch { /* ignore */ }
        },

        // ----- 用户管理 -----
        async loadUsers() {
            try {
                const [pending, all] = await Promise.all([API.pendingUsers(), API.allUsers()]);
                this.pendingUsers = pending || [];
                this.pendingCount = this.pendingUsers.length;
                this.allUsers = all || [];
            } catch { /* ignore */ }
        },
        async approveUser(id) {
            try {
                const data = await API.approveUser(id);
                if (data && data.success) this.loadUsers();
            } catch (err) { console.error('approve error', err); }
        },
        async rejectUser(id) {
            try {
                const data = await API.rejectUser(id);
                if (data && data.success) this.loadUsers();
            } catch (err) { console.error('reject error', err); }
        },
        canChangeRole(u) {
            if (this.isSuperAdmin) return u.username !== this.username;
            if (this.role === 'ROLE_ADMIN') return u.role === 'ROLE_USER' || (!u.approved);
            return false;
        },
        async changeUserRole(u) {
            const params = new URLSearchParams();
            params.set('role', u.role);
            await fetch('/admin/api/users/' + u.id + '/role', { method: 'POST', body: params });
        },
        roleLabel(r) {
            return { 'ROLE_SUPER_ADMIN': '超级管理员', 'ROLE_ADMIN': '管理员', 'ROLE_USER': '普通用户' }[r] || r;
        },

        // ----- 批量标记 -----
        toggleAll() {
            if (this.allSelected) { this.selectedIds = []; }
            else { this.selectedIds = this.codes.map(c => c.id); }
        },
        async batchUse() {
            if (this.selectedIds.length === 0) return;
            try {
                const data = await API.batchUse(this.selectedIds);
                if (data && data.success) {
                    this.addToast({ title: '批量标记', desc: '已批量标记 ' + data.count + ' 个激活码' });
                    this.selectedIds = [];
                    this.loadCodes(this.listFilter);
                }
            } catch { /* ignore */ }
        },
        async batchUnuse() {
            if (this.selectedIds.length === 0) return;
            try {
                const data = await API.batchUnuse(this.selectedIds);
                if (data && data.success) {
                    this.addToast({ title: '批量恢复', desc: '已恢复 ' + data.count + ' 个激活码' });
                    this.selectedIds = [];
                    this.loadCodes(this.listFilter);
                }
            } catch { /* ignore */ }
        },
        async doBatchDelete() {
            if (this.selectedIds.length === 0) return;
            try {
                const data = await API.batchDelete(this.selectedIds);
                if (data && data.success) {
                    this.addToast({ title: '批量删除', desc: '已批量删除 ' + data.count + ' 个激活码' });
                    this.selectedIds = [];
                    this.loadCodes(this.listFilter);
                }
            } catch { /* ignore */ }
            this.showBatchDeleteModal = false;
        },

        // ----- Excel 导入 -----
        onDrop(e) {
            this.dragOver = false;
            var files = e.dataTransfer?.files;
            if (files && files.length > 0) this.importFile = files[0];
        },
        onFileChange(e) {
            if (e.target.files && e.target.files.length > 0) this.importFile = e.target.files[0];
        },
        async doImport() {
            if (!this.importFile) { this.importResult = '请选择文件'; this.importOk = false; return; }
            this.importing = true;
            this.importResult = '';
            try {
                const data = await API.importExcel(this.importFile);
                this.importOk = data.success;
                if (data.success) {
                    this.importResult = '成功导入 ' + data.count + ' 个激活码';
                    this.importFile = null;
                    this.loadCodes(this.listFilter);
                } else {
                    this.importResult = data.message || '导入失败';
                }
            } catch (err) {
                this.importOk = false;
                this.importResult = '导入失败: ' + (err.message || '网络异常');
            } finally { this.importing = false; }
        },

        // ----- 删除用户 -----
        confirmDeleteUser(u) { this.deleteTarget = u; this.showDeleteModal = true; },
        async doDeleteUser() {
            if (!this.deleteTarget) return;
            try {
                const data = await API.deleteUser(this.deleteTarget.id);
                if (data && data.success) this.loadUsers();
            } catch { /* ignore */ }
            this.showDeleteModal = false;
            this.deleteTarget = null;
        },

        // ----- 密码修改 -----
        openPasswordModal(u) {
            this.pwdTarget = u;
            this.pwdIsSelf = !u || u.username === this.username;
            this.pwdOld = ''; this.pwdNew = ''; this.pwdNew2 = '';
            this.pwdMatch = true; this.pwdResult = '';
            this.showPasswordModal = true;
        },
        closePasswordModal() { this.showPasswordModal = false; this.pwdTarget = null; this.pwdResult = ''; },
        async doChangePassword() {
            var isSelf = this.pwdIsSelf;
            if (isSelf && (!this.pwdOld || this.pwdNew !== this.pwdNew2)) return;
            this.pwdLoading = true;
            this.pwdResult = '';
            try {
                var body = {
                    userId: isSelf ? null : this.pwdTarget.id,
                    oldPassword: isSelf ? this.pwdOld : null,
                    newPassword: this.pwdNew
                };
                const data = await API.changePassword(body);
                this.pwdResultOk = data.success;
                this.pwdResult = data.message || (data.success ? '密码修改成功' : '修改失败');
                if (data.success) { this.pwdOld = ''; this.pwdNew = ''; this.pwdNew2 = ''; }
            } catch { this.pwdResultOk = false; this.pwdResult = '网络错误，请重试'; }
            finally { this.pwdLoading = false; }
        },

        // ----- 通用 -----
        formatTime(ts) {
            if (!ts) return '—';
            const d = new Date(ts);
            if (isNaN(d.getTime())) {
                const parts = ts.slice(0, 19).split('T');
                if (parts.length === 2) return parts[0] + ' ' + parts[1].slice(0, 5);
                return ts;
            }
            const pad = n => String(n).padStart(2, '0');
            return d.getFullYear() + '-' + pad(d.getMonth()+1) + '-' + pad(d.getDate()) + ' ' + pad(d.getHours()) + ':' + pad(d.getMinutes());
        },
        copyToClipboard(text) {
            if (navigator.clipboard) {
                navigator.clipboard.writeText(text).catch(() => {});
            } else {
                var ta = document.createElement('textarea');
                ta.value = text;
                document.body.appendChild(ta);
                ta.select();
                document.execCommand('copy');
                document.body.removeChild(ta);
            }
        },
    }
}).mount('#app');
