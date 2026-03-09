const storyFeed = document.getElementById('story-feed');
const detailPane = document.getElementById('detail-pane');
let stories = [];
let selectedStoryId = null;

async function loadStories() {
    try {
        const response = await fetch('/api/stories');
        stories = await response.json();
        renderStories();
    } catch (e) {
        console.error('Failed to load stories:', e);
    }
}

function renderStories() {
    storyFeed.innerHTML = stories.map(story => createStoryCard(story)).join('');
}

function createStoryCard(story) {
    const analysis = story.analysis;
    const score = analysis ? (analysis.mms + analysis.sa + analysis.sd + analysis.d) / 4 : 0;
    const scoreFixed = score.toFixed(1);
    const categoryClass = (story.category || 'unknown').toLowerCase();
    const isActive = selectedStoryId === story.id;
    const alignmentLabel = analysis ? analysis.alignment.replace('_', ' ') : 'PENDING';
    const alignmentClass = analysis ? 'alignment-' + analysis.alignment.toLowerCase() : 'alignment-pending';
    const isContentLoaded = !!story.cleanText;

    return `
        <div onclick="selectStory('${story.id}')" class="relative bg-white dark:bg-slate-900 rounded-xl shadow-sm border-l-4 ${getBorderColor(categoryClass)} overflow-hidden hover:shadow-md transition-shadow group cursor-pointer ${isActive ? 'active ring-2 ring-primary bg-primary/5' : ''}">
            ${isContentLoaded ? '<div class="content-loaded-icon" title="Content Harvested"><span class="material-symbols-outlined fill-1">check_circle</span></div>' : ''}
            <div class="flex">
                <div class="w-24 shrink-0 flex flex-col items-center justify-center bg-primary/5 p-4 border-r border-primary/5">
                    <span class="signal-score text-3xl font-bold ${score > 7 ? 'text-primary' : 'text-slate-400'}">${scoreFixed}</span>
                    <span class="text-[10px] font-bold uppercase tracking-widest text-slate-400 mt-1">Signal</span>
                </div>
                <div class="flex-1 p-5">
                    <div class="flex items-start justify-between mb-2">
                        <div>
                            <div class="flex items-center gap-2 mb-1">
                                <span class="text-[10px] font-bold px-2 py-0.5 rounded ${alignmentClass}">${alignmentLabel}</span>
                                <span class="text-[10px] font-bold px-2 py-0.5 rounded ${getHypeBg(analysis)}">${analysis ? 'HYPE: ' + analysis.hypeRisk : 'WAITING'}</span>
                            </div>
                            <h3 class="font-bold text-lg group-hover:text-primary transition-colors">${story.title}</h3>
                            <p class="text-xs text-slate-400 mt-0.5">${new URL(story.url).hostname}</p>
                        </div>
                    </div>
                    <p class="text-sm text-slate-600 dark:text-slate-400 line-clamp-2 leading-relaxed italic">
                        "${analysis ? analysis.sparringNote : (story.categoryReasoning || 'Scanning story content...')}"
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

function selectStory(id) {
    selectedStoryId = id;
    const story = stories.find(s => s.id === id);
    renderStories();
    renderDetailPane(story);
}

function renderDetailPane(story) {
    if (!story) return;
    const analysis = story.analysis;

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
                <p class="text-sm text-slate-400">ID: ${story.id} • ${story.category || 'Categorizing...'}</p>
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
            ` : '<p class="text-center p-10 text-slate-400 italic">Deep SECV analysis in progress...</p>'}
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
            ${renderGauge('MMS', analysis.mms, 'text-primary')}
            ${renderGauge('SA', analysis.sa, 'text-blue-500')}
            ${renderGauge('SD', analysis.sd, 'text-red-500')}
            ${renderGauge('D', analysis.d, 'text-green-500')}
        </div>
    `;
}

function renderGauge(label, val, colorClass) {
    const offset = 150 - (val / 10 * 150);
    return `
        <div class="text-center">
            <div class="relative size-14 mx-auto mb-2">
                <svg class="size-full -rotate-90">
                    <circle class="text-slate-100 dark:text-slate-800" cx="28" cy="28" fill="transparent" r="24" stroke="currentColor" stroke-width="4"></circle>
                    <circle class="${colorClass}" cx="28" cy="28" fill="transparent" r="24" stroke="currentColor" stroke-dasharray="150" stroke-dashoffset="${offset}" stroke-width="4"></circle>
                </svg>
                <span class="absolute inset-0 flex items-center justify-center text-[10px] font-mono font-bold">${val}</span>
            </div>
            <span class="text-[9px] font-bold uppercase tracking-tighter text-slate-400">${label}</span>
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

function connectWS() {
    const socket = new WebSocket('ws://' + window.location.host + '/ws/events');
    socket.onmessage = (event) => {
        console.log('Update received');
        loadStories();
    };
    socket.onclose = () => setTimeout(connectWS, 3000);
}

loadStories();
connectWS();
