const { createApp, ref, reactive, computed, onMounted, onUnmounted, watch, nextTick } = Vue;

const app = createApp({
    setup() {
        // 状态
        const apps = ref([]);
        const alerts = ref([]);
        const selectedAppId = ref(null);
        const wsConnected = ref(false);
        const currentTime = ref('');
        const darkMode = ref(localStorage.getItem('jvm-doctor-theme') === 'dark');
        const showDrawer = ref(false);
        const showRegisterModal = ref(false);
        
        // 注册表单
        const registerForm = reactive({
            appName: '',
            host: '',
            port: '',
            jvmName: '',
            jvmVersion: '',
            startTime: ''
        });
        
        let ws = null;
        let charts = {};
        let metricsHistory = {};
        
        // 当前选中应用的实时指标
        const currentMetrics = reactive({
            heapUsed: 0,
            heapMax: 0,
            heapUsage: 0,
            nonheapUsed: 0,
            metaspaceUsed: 0,
            metaspaceMax: 0,
            metaspaceUsage: 0,
            threadCount: 0,
            daemonThreadCount: 0,
            cpuUsage: 0,
            systemLoad: 0,
            uptime: 0,
            gcCount: 0,
            gcTime: 0
        });
        
        // 计算属性
        const runningApps = computed(() => apps.value.filter(a => a.status === 'running'));
        const unacknowledgedCount = computed(() => alerts.value.filter(a => !a.acknowledged).length);
        const selectedApp = computed(() => apps.value.find(a => a.id === selectedAppId.value));
        
        // 性能建议
        const suggestions = computed(() => {
            const list = [];
            const m = currentMetrics;
            
            // 堆内存建议
            if (m.heapUsage >= 0.9) {
                list.push({
                    level: 'critical',
                    title: '堆内存使用率过高',
                    desc: `当前使用 ${(m.heapUsage * 100).toFixed(1)}%，建议增加堆内存或优化内存使用`
                });
            } else if (m.heapUsage >= 0.8) {
                list.push({
                    level: 'warning',
                    title: '堆内存使用率偏高',
                    desc: `当前使用 ${(m.heapUsage * 100).toFixed(1)}%，建议关注内存增长趋势`
                });
            } else if (m.heapUsage < 0.5 && m.heapMax > 0) {
                list.push({
                    level: 'info',
                    title: '堆内存使用率偏低',
                    desc: `当前使用 ${(m.heapUsage * 100).toFixed(1)}%，可考虑适当减少堆内存`
                });
            }
            
            // Metaspace 建议
            if (m.metaspaceUsage >= 0.85) {
                list.push({
                    level: 'warning',
                    title: 'Metaspace 使用率偏高',
                    desc: `当前使用 ${(m.metaspaceUsage * 100).toFixed(1)}%，可能存在类加载过多问题`
                });
            }
            
            // GC 建议
            if (m.gcCount > 100 && m.uptime > 3600000) {
                list.push({
                    level: 'warning',
                    title: 'GC 频率较高',
                    desc: `1小时内 GC ${m.gcCount} 次，建议优化对象创建或调整 GC 参数`
                });
            } else if (m.gcCount > 50 && m.uptime > 3600000) {
                list.push({
                    level: 'info',
                    title: 'GC 频率适中',
                    desc: `1小时内 GC ${m.gcCount} 次，建议持续关注`
                });
            }
            
            // 线程建议
            if (m.threadCount > 500) {
                list.push({
                    level: 'warning',
                    title: '线程数过多',
                    desc: `当前 ${m.threadCount} 个线程，建议检查是否存在线程泄漏`
                });
            } else if (m.threadCount > 200) {
                list.push({
                    level: 'info',
                    title: '线程数较多',
                    desc: `当前 ${m.threadCount} 个线程，建议关注线程池配置`
                });
            }
            
            // CPU 建议
            if (m.cpuUsage >= 0.9) {
                list.push({
                    level: 'critical',
                    title: 'CPU 使用率过高',
                    desc: `当前 ${(m.cpuUsage * 100).toFixed(1)}%，建议排查热点方法`
                });
            } else if (m.cpuUsage >= 0.8) {
                list.push({
                    level: 'warning',
                    title: 'CPU 使用率偏高',
                    desc: `当前 ${(m.cpuUsage * 100).toFixed(1)}%，建议关注`
                });
            }
            
            // 运行时长建议
            if (m.uptime > 604800000 && m.gcCount > 200) {
                list.push({
                    level: 'info',
                    title: '建议重启应用',
                    desc: `已运行 ${formatDuration(m.uptime)}，GC 较频繁，可考虑重启清理`
                });
            }
            
            return list;
        });
        
        // 注册应用
        const registerApp = async () => {
            try {
                const startTime = registerForm.startTime 
                    ? new Date(registerForm.startTime).getTime() 
                    : Date.now();
                
                await fetch('/api/apps/register', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        appName: registerForm.appName,
                        host: registerForm.host,
                        port: parseInt(registerForm.port),
                        jvmName: registerForm.jvmName,
                        jvmVersion: registerForm.jvmVersion,
                        startTime: startTime
                    })
                });
                
                // 重置表单
                registerForm.appName = '';
                registerForm.host = '';
                registerForm.port = '';
                registerForm.jvmName = '';
                registerForm.jvmVersion = '';
                registerForm.startTime = '';
                showRegisterModal.value = false;
                
                // 刷新列表
                loadApps();
            } catch (e) {
                console.error('Failed to register app:', e);
                alert('注册失败：' + e.message);
            }
        };
        
        // 下线应用
        const offlineApp = async (app) => {
            if (!confirm(`确定要下线应用 "${app.appName}" 吗？`)) return;
            
            try {
                await fetch(`/api/apps/${app.id}/offline`, { method: 'POST' });
                loadApps();
            } catch (e) {
                console.error('Failed to offline app:', e);
            }
        };
        
        // 恢复应用心跳
        const heartbeatApp = async (app) => {
            try {
                await fetch(`/api/apps/${app.id}/heartbeat`, { method: 'POST' });
                loadApps();
            } catch (e) {
                console.error('Failed to send heartbeat:', e);
            }
        };
        
        // 获取应用名称
        const getAppName = (appId) => {
            const app = apps.value.find(a => a.id === appId);
            return app ? app.appName : `App #${appId}`;
        };
        
        // 获取应用的最新指标
        const getAppMetric = (appId, metric) => {
            const history = metricsHistory[appId] || [];
            if (history.length === 0) return '-';
            const latest = history[history.length - 1];
            switch (metric) {
                case 'heapUsage':
                    return latest.heapUsage ? `${(latest.heapUsage * 100).toFixed(1)}%` : '-';
                case 'threadCount':
                    return latest.threadCount || '-';
                default:
                    return '-';
            }
        };
        
        // 选择应用
        const selectApp = async (app) => {
            selectedAppId.value = app.id;
            showDrawer.value = false;
            await nextTick();
            initCharts();
            await loadMetricsHistory(app.id);
        };
        
        // 确认告警
        const acknowledgeAlert = async (alertId) => {
            try {
                await fetch(`/api/alerts/${alertId}/acknowledge`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ acknowledgedBy: 'dashboard' })
                });
                loadAlerts();
            } catch (e) {
                console.error('Failed to acknowledge alert:', e);
            }
        };
        
        // 格式化字节
        const formatBytes = (bytes) => {
            if (!bytes) return '0 B';
            if (bytes < 1024) return bytes + ' B';
            if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
            if (bytes < 1024 * 1024 * 1024) return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
            return (bytes / (1024 * 1024 * 1024)).toFixed(2) + ' GB';
        };
        
        // 格式化时长
        const formatDuration = (ms) => {
            if (!ms) return '-';
            const seconds = Math.floor(ms / 1000);
            const minutes = Math.floor(seconds / 60);
            const hours = Math.floor(minutes / 60);
            const days = Math.floor(hours / 24);
            if (days > 0) return `${days}d ${hours % 24}h`;
            if (hours > 0) return `${hours}h ${minutes % 60}m`;
            if (minutes > 0) return `${minutes}m ${seconds % 60}s`;
            return `${seconds}s`;
        };
        
        // 格式化时间
        const formatTime = (timestamp) => {
            const date = new Date(timestamp);
            const now = new Date();
            const diff = now - date;
            if (diff < 60000) return '刚刚';
            if (diff < 3600000) return `${Math.floor(diff / 60000)} 分钟前`;
            if (diff < 86400000) return `${Math.floor(diff / 3600000)} 小时前`;
            return date.toLocaleString();
        };
        
        // 格式化日期
        const formatDate = (timestamp) => {
            if (!timestamp) return '-';
            return new Date(timestamp).toLocaleString('zh-CN');
        };
        
        // 格式化启动时间
        const formatUptime = (startTime) => {
            if (!startTime) return '-';
            return formatDuration(Date.now() - startTime);
        };
        
        // 获取使用率样式类
        const getUsageClass = (usage) => {
            if (!usage) return 'normal';
            if (usage >= 0.9) return 'danger';
            if (usage >= 0.7) return 'warning';
            return 'normal';
        };
        
        // 切换深色模式
        const toggleTheme = () => {
            darkMode.value = !darkMode.value;
            localStorage.setItem('jvm-doctor-theme', darkMode.value ? 'dark' : 'light');
            document.body.classList.toggle('dark-mode', darkMode.value);
            // 重新初始化图表以应用新颜色
            if (selectedAppId.value) {
                nextTick(() => initCharts());
            }
        };
        
        // 应用保存的主题
        const applyTheme = () => {
            if (darkMode.value) {
                document.body.classList.add('dark-mode');
            }
        };
        
        // 加载应用列表
        const loadApps = async () => {
            try {
                const res = await fetch('/api/apps');
                apps.value = await res.json();
            } catch (e) {
                console.error('Failed to load apps:', e);
            }
        };
        
        // 加载告警列表
        const loadAlerts = async () => {
            try {
                const res = await fetch('/api/alerts');
                alerts.value = await res.json();
            } catch (e) {
                console.error('Failed to load alerts:', e);
            }
        };
        
        // 加载指标历史
        const loadMetricsHistory = async (appId) => {
            try {
                const since = Date.now() - 3600000; // 最近1小时
                const res = await fetch(`/api/metrics/${appId}/history?since=${since}`);
                const data = await res.json();
                metricsHistory[appId] = data;
                updateCharts(data);
            } catch (e) {
                console.error('Failed to load metrics history:', e);
            }
        };
        
        // 更新图表
        const updateCharts = (data) => {
            if (!data || data.length === 0) return;
            
            const labels = data.map(m => new Date(m.timestamp).toLocaleTimeString());
            const heapData = data.map(m => m.heapUsage ? m.heapUsage * 100 : 0);
            const cpuData = data.map(m => m.cpuUsage ? m.cpuUsage * 100 : 0);
            const threadData = data.map(m => m.threadCount || 0);
            const metaspaceData = data.map(m => m.metaspaceUsage ? m.metaspaceUsage * 100 : 0);
            
            if (charts.heap) {
                charts.heap.data.labels = labels;
                charts.heap.data.datasets[0].data = heapData;
                charts.heap.update('none');
            }
            if (charts.cpu) {
                charts.cpu.data.labels = labels;
                charts.cpu.data.datasets[0].data = cpuData;
                charts.cpu.update('none');
            }
            if (charts.thread) {
                charts.thread.data.labels = labels;
                charts.thread.data.datasets[0].data = threadData;
                charts.thread.update('none');
            }
            if (charts.metaspace) {
                charts.metaspace.data.labels = labels;
                charts.metaspace.data.datasets[0].data = metaspaceData;
                charts.metaspace.update('none');
            }
        };
        
        // 初始化图表
        const initCharts = () => {
            const isDark = darkMode.value;
            const gridColor = isDark ? '#374151' : '#f1f5f9';
            const textColor = isDark ? '#9ca3af' : '#64748b';
            
            const chartOptions = {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: { display: false },
                    tooltip: {
                        backgroundColor: isDark ? '#1f2937' : 'white',
                        titleColor: isDark ? '#f3f4f6' : '#1a1a2e',
                        bodyColor: isDark ? '#d1d5db' : '#64748b',
                        borderColor: isDark ? '#374151' : '#e5e7eb',
                        borderWidth: 1,
                        padding: 8,
                        displayColors: false
                    }
                },
                scales: {
                    x: {
                        display: false,
                        grid: { display: false }
                    },
                    y: {
                        beginAtZero: true,
                        grid: { color: gridColor },
                        ticks: { color: textColor }
                    }
                },
                elements: {
                    point: { radius: 0, hoverRadius: 4 },
                    line: { tension: 0.4 }
                },
                animation: { duration: 0 }
            };
            
            const chartTypes = [
                { key: 'heap', label: '堆内存' },
                { key: 'cpu', label: 'CPU' },
                { key: 'thread', label: '线程' },
                { key: 'metaspace', label: 'Metaspace' }
            ];
            
            chartTypes.forEach(({ key }) => {
                const canvas = document.getElementById(`${key}Chart`);
                if (!canvas) return;
                
                const ctx = canvas.getContext('2d');
                const colors = {
                    heap: { bg: 'rgba(59, 130, 246, 0.2)', border: '#3b82f6' },
                    cpu: { bg: 'rgba(239, 68, 68, 0.2)', border: '#ef4444' },
                    thread: { bg: 'rgba(16, 185, 129, 0.2)', border: '#10b981' },
                    metaspace: { bg: 'rgba(168, 85, 247, 0.2)', border: '#a855f7' }
                }[key];
                
                if (charts[key]) {
                    charts[key].destroy();
                }
                
                charts[key] = new Chart(ctx, {
                    type: 'line',
                    data: {
                        labels: [],
                        datasets: [{
                            data: [],
                            backgroundColor: colors.bg,
                            borderColor: colors.border,
                            fill: true,
                            borderWidth: 2
                        }]
                    },
                    options: chartOptions
                });
            });
        };
        
        // 连接 WebSocket
        const connectWebSocket = () => {
            ws = new WebSocket(`ws://${window.location.host}/ws/metrics`);
            
            ws.onopen = () => {
                wsConnected.value = true;
                console.log('WebSocket connected');
            };
            
            ws.onclose = () => {
                wsConnected.value = false;
                console.log('WebSocket disconnected, reconnecting...');
                setTimeout(connectWebSocket, 3000);
            };
            
            ws.onerror = (err) => {
                console.error('WebSocket error:', err);
            };
            
            ws.onmessage = (event) => {
                try {
                    const data = JSON.parse(event.data);
                    handleWebSocketMessage(data);
                } catch (e) {
                    console.error('Failed to parse WebSocket message:', e);
                }
            };
        };
        
        // 处理 WebSocket 消息
        const handleWebSocketMessage = (data) => {
            if (data.type === 'metrics') {
                const appId = data.appId;
                
                // 更新历史数据
                if (!metricsHistory[appId]) {
                    metricsHistory[appId] = [];
                }
                metricsHistory[appId].push({
                    timestamp: data.timestamp,
                    heapUsed: data.heapUsed,
                    heapMax: data.heapMax,
                    heapUsage: data.heapUsage,
                    metaspaceUsed: data.metaspaceUsed,
                    metaspaceMax: data.metaspaceMax,
                    metaspaceUsage: data.metaspaceUsage,
                    threadCount: data.threadCount,
                    cpuUsage: data.cpuUsage,
                    gcCount: data.gcCount
                });
                
                // 只保留最近1小时数据
                const cutoff = Date.now() - 3600000;
                metricsHistory[appId] = metricsHistory[appId].filter(m => m.timestamp > cutoff);
                
                // 如果是当前选中的应用，更新实时指标
                if (appId === selectedAppId.value) {
                    Object.assign(currentMetrics, {
                        heapUsed: data.heapUsed,
                        heapMax: data.heapMax,
                        heapUsage: data.heapUsage,
                        metaspaceUsed: data.metaspaceUsed,
                        metaspaceMax: data.metaspaceMax,
                        metaspaceUsage: data.metaspaceUsage,
                        threadCount: data.threadCount,
                        cpuUsage: data.cpuUsage,
                        gcCount: data.gcCount,
                        gcTime: data.gcTime
                    });
                    updateCharts(metricsHistory[appId]);
                }
            } else if (data.type === 'alert') {
                loadAlerts();
            }
        };
        
        // 更新时间
        const updateTime = () => {
            const now = new Date();
            currentTime.value = now.toLocaleString();
        };
        
        // 定时任务
        let timeInterval;
        
        onMounted(() => {
            applyTheme();
            loadApps();
            loadAlerts();
            connectWebSocket();
            updateTime();
            timeInterval = setInterval(updateTime, 1000);
        });
        
        onUnmounted(() => {
            if (ws) ws.close();
            if (timeInterval) clearInterval(timeInterval);
            Object.values(charts).forEach(chart => chart?.destroy());
        });
        
        // 监听应用列表变化，重新加载指标历史
        watch(selectedAppId, (newId) => {
            if (newId) {
                loadMetricsHistory(newId);
            }
        });
        
        return {
            apps,
            alerts,
            selectedAppId,
            selectedApp,
            runningApps,
            unacknowledgedCount,
            currentMetrics,
            wsConnected,
            currentTime,
            darkMode,
            showDrawer,
            showRegisterModal,
            registerForm,
            suggestions,
            getAppName,
            getAppMetric,
            selectApp,
            registerApp,
            offlineApp,
            heartbeatApp,
            acknowledgeAlert,
            formatBytes,
            formatDuration,
            formatTime,
            formatDate,
            formatUptime,
            getUsageClass,
            toggleTheme
        };
    }
});

app.mount('#app');
