const storyFeed = document.getElementById('story-feed');
const detailPane = document.getElementById('detail-pane');
const searchInput = document.getElementById('search-input');

let stories = [];
let selectedStoryId = null;
let currentFilter = 'inbox'; // inbox, high-signal, opposite, noise
let currentSort = 'latest'; // latest, score
let searchQuery = '';
let lastLoadRequestId = 0;

async function loadStories() {
    const requestId = ++lastLoadRequestId;
    try {
        const response = await fetch('/api/stories');
        const data = await response.json();
        
        // Only update if this is still the latest request we sent
        if (requestId === lastLoadRequestId) {
            stories = data;
            renderStories();
            
            // Refresh detail pane if a story is currently selected to keep it in sync
            if (selectedStoryId) {
                const story = stories.find(s => s.id === selectedStoryId);
                if (story) {
                    renderDetailPane(story);
                }
            }
        }
    } catch (e) {
        console.error('Failed to load stories:', e);
    }
}

function getSignalScore(story) {
    if (!story.analysis) return 0;
    return (story.analysis.mms + story.analysis.sa + story.analysis.sd + story.analysis.d) / 4;
}

function renderStories() {
    // 1. Filter
    let filtered = stories.filter(s => {
        const score = getSignalScore(s);
        const matchesSearch = s.title.toLowerCase().includes(searchQuery.toLowerCase()) || 
                             (s.analysis && s.analysis.sparringNote.toLowerCase().includes(searchQuery.toLowerCase()));
        
        if (!matchesSearch) return false;

        switch (currentFilter) {
            case 'high-signal': return score >= 7.5;
            case 'opposite': return s.analysis && s.analysis.alignment === 'OPPOSITE_VIEW' && score >= 5.0;
            case 'noise': return (s.category === 'PRODUCT' || s.category === 'SOURCE' || (s.analysis && s.analysis.alignment === 'IRRELEVANT'));
            case 'failed': return s.failed;
            case 'inbox': 
            default:
                // Inbox excludes noise and failed by default
                const isNoise = (s.category === 'PRODUCT' || s.category === 'SOURCE' || (s.analysis && s.analysis.alignment === 'IRRELEVANT'));
                const isFailed = s.failed;
                return !isNoise && !isFailed;
        }
    });

    // 2. Sort
    filtered.sort((a, b) => {
        if (currentSort === 'score') {
            return getSignalScore(b) - getSignalScore(a);
        } else {
            // ID based sort (assuming higher ID is newer)
            return b.id.localeCompare(a.id);
        }
    });

    storyFeed.innerHTML = filtered.map(story => createStoryCard(story)).join('');
    document.getElementById('inbox-count').innerText = filtered.length;
}

// ... createStoryCard remains same ...

// Navigation Handlers
document.querySelectorAll('.nav-link').forEach(link => {
    link.addEventListener('click', (e) => {
        e.preventDefault();
        document.querySelectorAll('.nav-link').forEach(l => {
            l.classList.remove('bg-primary/10', 'text-slate-900', 'dark:text-primary', 'font-medium');
            l.classList.add('text-slate-600', 'dark:text-slate-300');
        });
        link.classList.add('bg-primary/10', 'text-slate-900', 'dark:text-primary', 'font-medium');
        link.classList.remove('text-slate-600', 'dark:text-slate-300');
        
        currentFilter = link.id.replace('nav-', '');
        renderStories();
    });
});

// Sort Handlers
document.querySelectorAll('.sort-btn').forEach(btn => {
    btn.addEventListener('click', () => {
        document.querySelectorAll('.sort-btn').forEach(b => {
            b.classList.remove('active', 'bg-primary/20', 'text-background-dark');
            b.classList.add('text-slate-500');
        });
        btn.classList.add('active', 'bg-primary/20', 'text-background-dark');
        btn.classList.remove('text-slate-500');
        
        currentSort = btn.id.replace('sort-', '');
        renderStories();
    });
});

// Search Handler
searchInput.addEventListener('input', (e) => {
    searchQuery = e.target.value;
    renderStories();
});

function capitalize(str) {
    if (!str) return '';
    return str.split('_')
              .map(word => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase())
              .join(' ');
}

function createStoryCard(story) {
    const analysis = story.analysis;
    const isIgnored = story.category === 'PRODUCT' || story.category === 'SOURCE';
    const score = analysis ? (analysis.mms + analysis.sa + analysis.sd + analysis.d) / 4 : 0;
    
    const isFailed = story.failed;
    const isPending = !isIgnored && !analysis && !isFailed;
    const scoreFixed = (isIgnored || isPending || isFailed) ? 'N/A' : score.toFixed(1);
    const scoreColorClass = (isIgnored || isPending || isFailed || score <= 7) ? 'text-slate-400' : 'text-primary';
    const scoreSizeClass = (isIgnored || isPending || isFailed) ? 'text-lg' : 'text-3xl';
    
    const categoryClass = (story.category || 'unknown').toLowerCase();
    const isActive = selectedStoryId === story.id;
    
    const alignmentLabel = isFailed ? 'Failed' : (isIgnored ? 'Skipped' : (analysis ? capitalize(analysis.alignment) : 'Pending'));
    const alignmentClass = isFailed ? 'alignment-failed' : (isIgnored ? 'alignment-pending' : (analysis ? 'alignment-' + analysis.alignment.toLowerCase() : 'alignment-pending'));
    const isContentLoaded = !!story.cleanText;

    return `
        <div onclick="selectStory('${story.id}')" class="relative bg-white dark:bg-slate-900 rounded-xl shadow-sm border-l-4 ${getBorderColor(categoryClass)} overflow-hidden hover:shadow-md transition-shadow group cursor-pointer ${isActive ? 'active ring-2 ring-primary bg-primary/5' : ''}">
            <div class="status-actions">
                ${isContentLoaded ? '<div class="content-loaded-icon" title="Content Harvested"><span class="material-symbols-outlined fill-1">check_circle</span></div>' : ''}
                <div class="reload-btn" onclick="event.stopPropagation(); reloadStory('${story.id}', this)" title="Force Re-Analysis">
                    <span class="material-symbols-outlined">refresh</span>
                </div>
            </div>
            <div class="flex">
                <div class="w-24 shrink-0 flex flex-col items-center justify-center bg-primary/5 p-4 border-r border-primary/5">
                    <span class="signal-score ${scoreSizeClass} font-bold ${scoreColorClass}">${scoreFixed}</span>
                    <span class="text-[10px] font-bold uppercase tracking-widest text-slate-400 mt-1">Signal</span>
                </div>
                <div class="flex-1 p-5">
                    <div class="flex items-start justify-between mb-2">
                        <div>
                            <div class="flex items-center gap-2 mb-1">
                                <span class="text-[10px] font-bold px-2 py-0.5 rounded ${alignmentClass}">${alignmentLabel}</span>
                                ${analysis && analysis.hypeRisk !== 'LOW' ? `<span class="text-[10px] font-bold px-2 py-0.5 rounded ${getHypeBg(analysis)}">Hype: ${capitalize(analysis.hypeRisk)}</span>` : ''}
                                ${story.aiInvolvement && story.aiInvolvement !== 'UNKNOWN' ? `<span class="text-[10px] font-bold px-2 py-0.5 rounded ${getAiInvolvementBg(story.aiInvolvement)}">${getAiInvolvementLabel(story.aiInvolvement)}</span>` : ''}
                            </div>
                            <h3 class="font-bold text-lg group-hover:text-primary transition-colors">${story.title}</h3>
                            <p class="text-xs text-slate-400 mt-0.5">${new URL(story.url).hostname}</p>
                        </div>
                    </div>
                    <p class="text-sm text-slate-600 dark:text-slate-400 line-clamp-2 leading-relaxed italic">
                        "${analysis ? analysis.sparringNote : (isFailed ? 'Error during processing. Click to retry.' : (story.categoryReasoning || 'Scanning story content...'))}"
                    </p>
                </div>
            </div>
        </div>
    `;
}

function getBorderColor(cat) {
    if (cat === 'writing') return 'border-green-500';
    if (cat === 'video') return 'border-red-500';
    if (cat === 'demo') return 'border-purple-500';
    if (cat === 'product') return 'border-orange-500';
    return 'border-slate-300';
}

function getHypeBg(analysis) {
    if (!analysis) return 'bg-slate-100 text-slate-500';
    if (analysis.hypeRisk === 'HIGH') return 'bg-red-100 text-red-700';
    if (analysis.hypeRisk === 'LOW') return 'bg-green-100 text-green-700';
    return 'bg-yellow-100 text-yellow-700';
}

function getAiInvolvementBg(involvement) {
    switch (involvement) {
        case 'HAND_CRAFTED': return 'bg-emerald-100 text-emerald-800';
        case 'ASSISTED': return 'bg-teal-100 text-teal-800';
        case 'COLLABORATIVE': return 'bg-blue-100 text-blue-800';
        case 'HIGH_AI': return 'bg-orange-100 text-orange-800';
        case 'PURE_SLOP': return 'bg-red-100 text-red-800';
        case 'UNKNOWN':
        default: return 'bg-slate-100 text-slate-500';
    }
}

function getAiInvolvementLabel(involvement) {
    switch (involvement) {
        case 'HAND_CRAFTED': return 'Hand Crafted';
        case 'ASSISTED': return 'AI Assisted';
        case 'COLLABORATIVE': return 'AI Collaborated';
        case 'HIGH_AI': return 'Mostly AI';
        case 'PURE_SLOP': return 'AI Slop';
        case 'UNKNOWN': return 'Unknown Slop';
        default: return involvement;
    }
}

async function reloadStory(id, btn) {
    btn.classList.add('loading');
    try {
        const response = await fetch(`/api/stories/${id}/reload`, { method: 'POST' });
        if (response.ok) {
            console.log(`Reload triggered for ${id}`);
        }
    } catch (e) {
        console.error('Failed to trigger reload:', e);
    } finally {
        setTimeout(() => btn.classList.remove('loading'), 1000);
    }
}

function selectStory(id) {
    selectedStoryId = id;
    const story = stories.find(s => s.id === id);
    renderStories();
    renderDetailPane(story);
}

function renderDetailPane(story) {
    if (!story) return;
    const analysis = story.analysis;
    const isIgnored = story.category === 'PRODUCT' || story.category === 'SOURCE';

    detailPane.innerHTML = `
        <div class="p-6 border-b border-primary/10">
            <div class="flex items-center justify-between mb-6">
                <button onclick="resetDetail()" class="flex items-center gap-1 text-slate-500 hover:text-primary transition-colors">
                    <span class="material-symbols-outlined">close</span>
                    <span class="text-xs font-bold uppercase tracking-wider">Close Dive</span>
                </button>
                <div class="flex gap-2">
                    <a href="${story.url}" target="_blank" class="flex items-center gap-2 px-4 py-1.5 bg-primary text-background-dark rounded-lg text-xs font-bold hover:opacity-90 transition-all">
                        <span class="material-symbols-outlined text-sm">auto_stories</span>
                        READ FULL
                    </a>
                </div>
            </div>
            <div class="mb-6">
                <h2 class="text-2xl font-bold leading-tight mb-2">${story.title}</h2>
                <div class="flex items-center gap-2">
                    <p class="text-sm text-slate-400">ID: ${story.id} • ${story.category || 'Categorizing...'}</p>
                    ${story.aiInvolvement && story.aiInvolvement !== 'UNKNOWN' ? `
                        <span class="size-1 rounded-full bg-slate-300"></span>
                        <span class="text-[10px] font-bold px-2 py-0.5 rounded ${getAiInvolvementBg(story.aiInvolvement)}">${getAiInvolvementLabel(story.aiInvolvement)}</span>
                    ` : ''}
                </div>
            </div>
            ${analysis ? renderScores(analysis) : ''}
        </div>
        <div class="p-6 space-y-8">
            ${analysis ? `
                <section>
                    <div class="flex items-center gap-2 mb-3">
                        <span class="material-symbols-outlined text-primary">psychology</span>
                        <h3 class="text-sm font-bold uppercase tracking-wider">Sparring Note</h3>
                    </div>
                    <div class="bg-primary/5 border border-primary/10 p-4 rounded-lg">
                        <p class="text-sm leading-relaxed text-slate-700 dark:text-slate-300 italic">"${analysis.sparringNote}"</p>
                    </div>
                </section>
                ${analysis.reasoningBullets && analysis.reasoningBullets.length > 0 ? `
                <section>
                    <div class="flex items-center gap-2 mb-3">
                        <span class="material-symbols-outlined text-primary">account_tree</span>
                        <h3 class="text-sm font-bold uppercase tracking-wider">Reasoning</h3>
                    </div>
                    <ul class="space-y-3">
                        ${analysis.reasoningBullets.map(bullet => `
                        <li class="flex items-start gap-3">
                            <span class="size-1.5 rounded-full bg-primary mt-1.5 shrink-0"></span>
                            <p class="text-xs text-slate-600 dark:text-slate-400"><span class="font-bold text-slate-800 dark:text-slate-200">${bullet.title}:</span> ${bullet.description}</p>
                        </li>
                        `).join('')}
                    </ul>
                </section>
                ` : ''}
            ` : (isIgnored ? `
                <div class="p-10 text-center bg-slate-50 dark:bg-slate-900/50 rounded-xl border border-dashed border-slate-200 dark:border-slate-800">
                    <span class="material-symbols-outlined text-slate-300 text-4xl mb-3">block</span>
                    <p class="text-sm text-slate-500 italic">Deep SECV analysis is currently disabled for ${story.category} content to maintain high signal focus. Only articles, videos, and demos are analyzed.</p>
                </div>
            ` : (story.failed ? `
                <div class="p-10 text-center bg-red-50 dark:bg-red-900/10 rounded-xl border border-dashed border-red-200 dark:border-red-800">
                    <span class="material-symbols-outlined text-red-400 text-4xl mb-3">error</span>
                    <p class="text-sm text-red-600 italic">An error occurred during the deep analysis process. You can try to force a re-analysis using the refresh button on the story card.</p>
                </div>
            ` : '<p class="text-center p-10 text-slate-400 italic">Deep SECV analysis in progress...</p>'))}
            <section>
                <div class="flex items-center gap-2 mb-3">
                    <span class="material-symbols-outlined text-primary">terminal</span>
                    <h3 class="text-sm font-bold uppercase tracking-wider">Content Preview</h3>
                </div>
                <div class="bg-slate-50 dark:bg-slate-900 p-4 rounded-lg font-mono text-[11px] leading-normal text-slate-600 dark:text-slate-400 border border-primary/5 h-96 overflow-y-auto">
                    ${story.cleanText ? story.cleanText.replace(/\n/g, '<br>') : 'Fetching content...'}
                </div>
            </section>
        </div>
    `;
}

function renderScores(analysis) {
    return `
        <div class="grid grid-cols-4 gap-4 mb-8">
            ${renderGauge('Mental Model Shift', analysis.mms, 'text-primary', 'Does this change how I think, or just confirm what I know?')}
            ${renderGauge('Strategic Actionability', analysis.sa, 'text-blue-500', 'Can I make a concrete decision or take action based on this?')}
            ${renderGauge('Signal Density', analysis.sd, 'text-red-500', 'Is it high-information "meat" or low-signal "fluff"?')}
            ${renderGauge('Durability', analysis.d, 'text-green-500', 'Will this still be valuable in 2 years?')}
        </div>
    `;
}

function renderGauge(label, val, colorClass, tooltip) {
    const offset = 150 - (val / 10 * 150);
    return `
        <div class="text-center flex flex-col items-center justify-start h-full" title="${tooltip}">
            <div class="relative size-14 mb-2 cursor-help shrink-0">
                <svg class="size-full -rotate-90">
                    <circle class="text-slate-100 dark:text-slate-800" cx="28" cy="28" fill="transparent" r="24" stroke="currentColor" stroke-width="4"></circle>
                    <circle class="${colorClass}" cx="28" cy="28" fill="transparent" r="24" stroke="currentColor" stroke-dasharray="150" stroke-dashoffset="${offset}" stroke-width="4"></circle>
                </svg>
                <span class="absolute inset-0 flex items-center justify-center text-[10px] font-mono font-bold">${val}</span>
            </div>
            <span class="text-[9px] font-bold tracking-tight text-slate-400 cursor-help leading-tight">${label}</span>
        </div>
    `;
}


function resetDetail() {
    selectedStoryId = null;
    renderStories();
    detailPane.innerHTML = `
        <div class="p-12 text-center text-slate-400 mt-20">
            <span class="material-symbols-outlined text-6xl mb-4">analytics</span>
            <p>Select a story to dive deep</p>
        </div>
    `;
}

let activeWorkers = 0;
const activityLog = document.getElementById('activity-log');
const activeWorkersCount = document.getElementById('active-workers-count');
let pendingStarted = new Set(); // To track active workers correctly

async function loadRecentActivity() {
    try {
        const response = await fetch('/api/activity/recent');
        const events = await response.json();
        // Events from server are already in reverse chronological order (newest first)
        // We want to process them to calculate workers and then render
        
        // Clear log first
        if (activityLog) activityLog.innerHTML = '';
        
        // Process in reverse (oldest to newest) to build state correctly
        [...events].reverse().forEach(event => {
            handleActivityEvent(event, false); // false = don't animate initial load
        });
    } catch (e) {
        console.error('Failed to load recent activity:', e);
    }
}

function connectWS() {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const socket = new WebSocket(protocol + '//' + window.location.host + '/ws/events');
    socket.onmessage = (event) => {
        const data = JSON.parse(event.data);
        
        if (data.handler) {
            handleActivityEvent(data, true);
            
            // Critical fix: If StoryProjector just finished, it means the repository
            // is now up-to-date with the domain event. Trigger a reload now.
            if (data.handler === 'StoryProjector' && data.elapsedMs !== undefined) {
                console.log('StoryProjector finished, refreshing stories');
                loadStories();
            }
        } else {
            console.log('Domain update received:', data);
            // Reload on ProcessingFailed because it might not go through StoryProjector
            // or we want immediate feedback.
            if (data.errorMessage) {
                loadStories();
            }
        }
    };
    socket.onclose = () => setTimeout(connectWS, 3000);
}

function handleActivityEvent(event, animate = true) {
    const isFinished = event.elapsedMs !== undefined;
    const key = event.executionId;

    // Server provides a snapshot of active workers in every event
    activeWorkers = event.activeWorkers;

    if (isFinished) {
        pendingStarted.delete(key);
    } else {
        pendingStarted.add(key);
    }
    
    addActivityLogEntry(event, animate);
    updateWorkersUI();
}

function updateWorkersUI() {
    if (!activeWorkersCount) return;
    activeWorkersCount.innerText = `${activeWorkers} WORKER${activeWorkers !== 1 ? 'S' : ''}`;
    const pill = document.getElementById('active-workers-pill');
    if (activeWorkers > 0) {
        pill.classList.remove('opacity-50');
    } else {
        pill.classList.add('opacity-50');
    }
}

function addActivityLogEntry(event, animate = true) {
    if (!activityLog) return;
    const isFinished = event.elapsedMs !== undefined;
    
    let message = '';
    if (isFinished) {
        message = `<span class="font-bold text-slate-700">${event.handler}</span> processed ${event.event} in ${event.elapsedMs}ms ${event.success ? '✅' : '❌'}`;
    } else {
        message = `<span class="font-bold text-slate-700">${event.handler}</span> started ${event.event}`;
    }
    
    if (event.storyId) {
        message += ` <span class="text-slate-400 font-mono">[${event.storyId}]</span>`;
    }

    const html = `
        <div class="flex gap-2 ${animate ? 'animate-in fade-in slide-in-from-left-2 duration-300' : ''}">
            <div class="w-0.5 bg-primary/20 relative"><div class="absolute top-0 left-[-1px] size-1 rounded-full bg-primary"></div></div>
            <p class="text-[9px] leading-tight text-slate-500">${message}</p>
        </div>
    `;
    
    activityLog.insertAdjacentHTML('afterbegin', html);
    
    while (activityLog.children.length > 20) {
        activityLog.lastElementChild.remove();
    }
}

loadStories();
loadRecentActivity();
connectWS();
updateWorkersUI();
