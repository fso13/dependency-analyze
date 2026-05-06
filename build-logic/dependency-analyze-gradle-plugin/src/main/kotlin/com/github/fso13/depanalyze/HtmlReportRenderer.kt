package com.github.fso13.depanalyze

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

internal class HtmlReportRenderer {
  private val mapper = jacksonObjectMapper()

  fun render(title: String, policyUri: String?, report: DependencyReport): String {
    val doc = Document.createShell("")
    doc.outputSettings().prettyPrint(true)

    doc.head().appendElement("meta").attr("charset", "utf-8")
    doc.head().appendElement("meta").attr("name", "viewport").attr("content", "width=device-width, initial-scale=1")
    doc.title(title)
    doc.head().appendElement("style").append(
      """
      :root { --bg:#0b1220; --panel:#111b2e; --text:#e7eefc; --muted:#a9b7d1; --accent:#6ea8fe; --danger:#ff6b6b; --ok:#51cf66; --chip:#22314f; --border:#22314f;}
      body { margin:0; font-family: ui-sans-serif, system-ui, -apple-system, Segoe UI, Roboto, Arial, "Apple Color Emoji","Segoe UI Emoji"; background:var(--bg); color:var(--text);}
      a { color: var(--accent); text-decoration:none; }
      a:hover { text-decoration:underline; }
      .wrap { max-width: 1320px; margin: 0 auto; padding: 20px; }
      .header { display:flex; gap:16px; align-items:flex-start; justify-content:space-between; margin-bottom: 14px; }
      .title { font-size: 20px; font-weight: 700; }
      .subtitle { margin-top: 6px; color: var(--muted); font-size: 13px; }
      .panel { background:var(--panel); border:1px solid var(--border); border-radius:12px; padding: 14px; }
      .filters { display:grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 10px; align-items:end; }
      .filters .row { display:flex; flex-direction:column; gap:6px; }
      .chipsRow { display:flex; flex-wrap:wrap; gap:6px; margin-top:6px; min-height: 24px; }
      .filterChip { background:#16233d; border:1px solid var(--border); color:var(--text); border-radius:999px; padding:2px 8px; font-size:11px; display:inline-flex; align-items:center; gap:6px; }
      .filterChip button { background:transparent; border:none; color:var(--muted); cursor:pointer; font-size:12px; line-height:1; padding:0; }
      .filterChip button:hover { color:var(--text); }
      label { color: var(--muted); font-size: 12px; }
      input, select { background:#0f1930; border:1px solid var(--border); color: var(--text); border-radius:10px; padding: 10px; outline:none; }
      input:focus, select:focus { border-color: var(--accent); }
      .nativeMulti { display:none; }
      .multiWrap { position:relative; }
      .multiBtn {
        width:100%;
        text-align:left;
        background:#0f1930;
        border:1px solid var(--border);
        color:var(--text);
        border-radius:10px;
        padding:10px;
        cursor:pointer;
      }
      .multiBtn:focus, .multiBtn:hover { border-color:var(--accent); }
      .multiMenu {
        position:absolute;
        left:0;
        right:0;
        top:calc(100% + 4px);
        max-height:240px;
        overflow:auto;
        background:#0f1930;
        border:1px solid var(--border);
        border-radius:10px;
        display:none;
        z-index:20;
        box-shadow:0 12px 28px rgba(0,0,0,.35);
      }
      .multiMenu.open { display:block; }
      .multiOption {
        display:flex;
        align-items:center;
        gap:8px;
        padding:8px 10px;
        cursor:pointer;
        font-size:13px;
      }
      .multiOption:hover { background:#16233d; }
      .multiOption input { margin:0; }
      .stats { display:flex; flex-wrap:wrap; gap:10px; margin-top: 12px; }
      .actions { display:flex; justify-content:space-between; align-items:center; margin-top:10px; gap:8px; }
      .actionsRight { display:flex; gap:8px; }
      .chip { background:var(--chip); border:1px solid var(--border); padding: 6px 10px; border-radius:999px; color: var(--muted); font-size: 12px; }
      .tableWrap { margin-top: 14px; overflow:auto; border-radius: 12px; border: 1px solid var(--border); }
      table { width:100%; border-collapse: collapse; min-width: 1100px; background: var(--panel); }
      th, td { padding: 10px 12px; border-bottom: 1px solid var(--border); vertical-align: top; }
      th { text-align:left; font-size: 12px; color: var(--muted); position: sticky; top:0; background: #0f1930; z-index: 1; }
      td { font-size: 13px; }
      .mono { font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace; }
      .bad { color: var(--danger); font-weight: 600; }
      .ok { color: var(--ok); font-weight: 600; }
      .small { font-size: 12px; color: var(--muted); }
      .pill { display:inline-block; padding: 3px 8px; border-radius: 999px; border:1px solid var(--border); background: var(--chip); margin: 2px 6px 2px 0; font-size: 12px; }
      .nowrap { white-space: nowrap; }
      .vuln { margin: 6px 0; padding: 8px 10px; border:1px solid var(--border); border-radius: 10px; background:#0f1930; }
      .vuln .top { display:flex; gap:10px; align-items:baseline; justify-content:space-between; }
      .vuln .id { font-weight:700; }
      .vuln .desc { margin-top: 6px; line-height: 1.35; white-space: pre-wrap; }
      .vuln .rec { margin-top: 6px; color: var(--text); }
      .muted { color: var(--muted); }
      .sev { display:inline-block; padding:2px 8px; border-radius:999px; font-size:11px; border:1px solid var(--border); margin-right:6px; }
      .sev.critical { color:#ff8787; border-color:#7a2f2f; background:#2a1414; }
      .sev.high { color:#ffb267; border-color:#6d4b1e; background:#2a2013; }
      .sev.medium { color:#ffe066; border-color:#6f621f; background:#29250f; }
      .sev.low { color:#8ce99a; border-color:#2e6b39; background:#112316; }
      .sev.unknown { color:#ced4da; border-color:#495057; background:#1a1f24; }
      .btn { cursor:pointer; background:#1a2843; color:var(--text); border:1px solid var(--border); border-radius:10px; padding:6px 10px; }
      .btn:hover { border-color:var(--accent); }
      .modalBackdrop { position:fixed; inset:0; background:rgba(0,0,0,.55); display:none; align-items:center; justify-content:center; z-index:50; }
      .modal { width:min(1200px, 94vw); max-height:88vh; overflow:auto; background:var(--panel); border:1px solid var(--border); border-radius:12px; padding:14px; }
      .modalHeader { display:flex; align-items:flex-start; justify-content:space-between; gap:12px; margin-bottom:10px; }
      .modalTitle { font-size:16px; font-weight:700; }
      .modalFilters { display:flex; gap:10px; flex-wrap:wrap; margin-bottom:10px; }
      .modal table { min-width: 100%; }
      .modal td { font-size:12px; }
      """.trimIndent()
    )

    val wrap = doc.body().appendElement("div").addClass("wrap")

    val header = wrap.appendElement("div").addClass("header")
    val left = header.appendElement("div")
    left.appendElement("div").addClass("title").text(title)
    left.appendElement("div").addClass("subtitle").text("Generated at ${report.generatedAtIso} • Root: ${report.rootProject}")
    if (!policyUri.isNullOrBlank()) {
      left.appendElement("div").addClass("subtitle").append("Policy: ").appendElement("a").attr("href", policyUri).text(policyUri)
    }

    val panel = wrap.appendElement("div").addClass("panel")
    val filters = panel.appendElement("div").addClass("filters")
    filters.appendFilter("Search", "search", "text", "group:name or module path…")
    filters.appendSelect("Module", "moduleFilter", multiple = true)
    filters.appendSelect("Scope", "scopeFilter", multiple = true)
    filters.appendSelect("License", "licenseFilter", multiple = true)
    filters.appendSelect("Relation", "relationFilter", multiple = true)
    filters.appendSelect("Vulnerability", "vulnFilter", multiple = false)
    filters.appendSelect("Vuln severity", "vulnSeverityFilterMain", multiple = true)

    panel.appendElement("div").addClass("stats").attr("id", "stats")
    val actions = panel.appendElement("div").addClass("actions")
    actions.appendElement("button")
      .attr("id", "clearFiltersBtn")
      .attr("type", "button")
      .addClass("btn")
      .text("Clear filters")

    val actionsRight = actions.appendElement("div").addClass("actionsRight")
    actionsRight.appendElement("button")
      .attr("id", "exportXlsxBtn")
      .attr("type", "button")
      .addClass("btn")
      .text("Export XLSX")
    actionsRight.appendElement("button")
      .attr("id", "exportPdfBtn")
      .attr("type", "button")
      .addClass("btn")
      .text("Export PDF")

    val tableWrap = wrap.appendElement("div").addClass("tableWrap")
    val table = tableWrap.appendElement("table")
    val thead = table.appendElement("thead").appendElement("tr")
    listOf("Module", "Config", "Dependency", "Latest", "Type", "Relation", "Licenses", "Vulnerabilities").forEach { thead.appendElement("th").text(it) }
    table.appendElement("tbody").attr("id", "rows")

    val json = mapper.writeValueAsString(report)
    doc.body().appendElement("script")
      .attr("type", "application/json")
      .attr("id", "report-data")
      .append(json)
    doc.body().appendElement("script")
      .attr("src", "https://cdn.jsdelivr.net/npm/xlsx@0.18.5/dist/xlsx.full.min.js")
    doc.body().appendElement("script")
      .attr("src", "https://cdn.jsdelivr.net/npm/jspdf@2.5.1/dist/jspdf.umd.min.js")
    doc.body().appendElement("script")
      .attr("src", "https://cdn.jsdelivr.net/npm/jspdf-autotable@3.8.2/dist/jspdf.plugin.autotable.min.js")

    doc.body().appendElement("div").attr("id", "vulnModalBackdrop").addClass("modalBackdrop").append(
      """
      <div class="modal" id="vulnModal" role="dialog" aria-modal="true">
        <div class="modalHeader">
          <div>
            <div class="modalTitle" id="vulnModalTitle">Vulnerabilities</div>
            <div class="small" id="vulnModalSubtitle"></div>
          </div>
          <button class="btn" id="vulnModalClose" type="button">Close</button>
        </div>
        <div class="modalFilters">
          <div class="row">
            <label for="vulnSeverityFilter">Severity</label>
            <select id="vulnSeverityFilter">
              <option value="">(all)</option>
              <option value="critical">critical</option>
              <option value="high">high</option>
              <option value="medium">medium</option>
              <option value="low">low</option>
              <option value="unknown">unknown</option>
            </select>
          </div>
          <div class="row">
            <label for="vulnSearch">Search</label>
            <input id="vulnSearch" type="text" placeholder="id/title/description..."/>
          </div>
        </div>
        <div class="tableWrap">
          <table>
            <thead>
              <tr>
                <th>ID</th>
                <th>Severity</th>
                <th>CVSS</th>
                <th>Title</th>
                <th>Description</th>
                <th>Recommendation</th>
                <th>Reference</th>
              </tr>
            </thead>
            <tbody id="vulnModalRows"></tbody>
          </table>
        </div>
      </div>
      """.trimIndent()
    )

    doc.body().appendElement("script").append(
      """
      const REPORT = JSON.parse(document.getElementById('report-data').textContent);
      const entries = REPORT.entries || [];

      const $ = (id) => document.getElementById(id);
      const searchEl = $('search');
      const moduleEl = $('moduleFilter');
      const scopeEl = $('scopeFilter');
      const licEl = $('licenseFilter');
      const relationEl = $('relationFilter');
      const vulnEl = $('vulnFilter');
      const vulnSeverityEl = $('vulnSeverityFilterMain');
      const clearFiltersBtn = $('clearFiltersBtn');
      const rowsEl = $('rows');
      const statsEl = $('stats');
      const exportXlsxBtn = $('exportXlsxBtn');
      const exportPdfBtn = $('exportPdfBtn');
      const vulnBackdrop = $('vulnModalBackdrop');
      const vulnClose = $('vulnModalClose');
      const vulnRows = $('vulnModalRows');
      const vulnSeverityFilter = $('vulnSeverityFilter');
      const vulnSearch = $('vulnSearch');
      const vulnTitle = $('vulnModalTitle');
      const vulnSubtitle = $('vulnModalSubtitle');
      let activeVulns = [];
      let lastFiltered = [];
      const SELECT_ALL_VALUE = '__all__';
      const SELECT_ALL_LABEL = 'Выбрать все';

      function norm(s){ return (s || '').toString().toLowerCase(); }
      function uniq(arr){ return Array.from(new Set(arr)).sort((a,b)=>a.localeCompare(b)); }
      function esc(s){
        return (s || '').toString()
          .replaceAll('&','&amp;')
          .replaceAll('<','&lt;')
          .replaceAll('>','&gt;')
          .replaceAll('"','&quot;')
          .replaceAll("'",'&#39;');
      }
      function severity(v){
        const score = Number(v && v.cvssScore);
        if (!Number.isFinite(score)) return 'unknown';
        if (score >= 9) return 'critical';
        if (score >= 7) return 'high';
        if (score >= 4) return 'medium';
        if (score > 0) return 'low';
        return 'unknown';
      }
      function severitySummary(vulns){
        const out = {critical:0, high:0, medium:0, low:0, unknown:0};
        (vulns || []).forEach(v => out[severity(v)]++);
        return out;
      }

      function licenseNames(e){
        if (!e.licenses || e.licenses.length === 0) return ['(unknown)'];
        const names = e.licenses.map(l => l.name || l.url || '(unknown)').filter(Boolean);
        return names.length ? names : ['(unknown)'];
      }

      function depLabel(e){
        if (e.type === 'PROJECT') return e.name;
        const g = e.group ? e.group + ':' : '';
        const v = e.version ? ':' + e.version : '';
        return g + e.name + v;
      }
      function relationLabel(e){
        if (e.type === 'PROJECT') return 'project-direct';
        return e.isTransitive ? 'transitive' : 'direct';
      }
      function latestLabel(e){
        if (e.type !== 'EXTERNAL') return '-';
        return e.latestVersion || '-';
      }

      function vulnCount(e){ return (e.vulnerabilities || []).length; }
      function entrySeverities(e){
        const levels = new Set((e.vulnerabilities || []).map(v => severity(v)));
        return Array.from(levels);
      }
      function renderVulnModalRows(){
        const sev = vulnSeverityFilter.value;
        const q = norm(vulnSearch.value);
        const filtered = activeVulns.filter(v => {
          const s = severity(v);
          if (sev && s !== sev) return false;
          if (q) {
            const hay = norm((v.id||'') + ' ' + (v.title||'') + ' ' + (v.description||'') + ' ' + (v.recommendation||'') + ' ' + (v.reference||''));
            if (!hay.includes(q)) return false;
          }
          return true;
        });

        vulnRows.innerHTML = '';
        filtered.forEach(v => {
          const tr = document.createElement('tr');
          const sev = severity(v);
          tr.innerHTML =
            '<td class="mono">' + esc(v.id || '') + '</td>' +
            '<td><span class="sev ' + sev + '">' + sev + '</span></td>' +
            '<td class="mono">' + esc(v.cvssScore ? String(v.cvssScore) : '') + '</td>' +
            '<td>' + esc(v.title || '') + '</td>' +
            '<td>' + esc(v.description || '') + '</td>' +
            '<td>' + esc(v.recommendation || '') + '</td>' +
            '<td>' + (v.reference ? '<a href="' + esc(v.reference) + '" target="_blank" rel="noreferrer">' + esc(v.reference) + '</a>' : '') + '</td>';
          vulnRows.appendChild(tr);
        });
      }
      function openVulnModal(depName, vulns){
        activeVulns = vulns || [];
        vulnSeverityFilter.value = '';
        vulnSearch.value = '';
        vulnTitle.textContent = 'Vulnerabilities';
        vulnSubtitle.textContent = depName + ' • total: ' + activeVulns.length;
        renderVulnModalRows();
        vulnBackdrop.style.display = 'flex';
      }
      function closeVulnModal(){ vulnBackdrop.style.display = 'none'; }
      vulnClose.addEventListener('click', closeVulnModal);
      vulnBackdrop.addEventListener('click', (e) => { if (e.target === vulnBackdrop) closeVulnModal(); });
      vulnSeverityFilter.addEventListener('change', renderVulnModalRows);
      vulnSearch.addEventListener('input', renderVulnModalRows);

      function selectedValues(selectEl){
        if (selectEl._multiApi) return selectEl._multiApi.getValues();
        return Array.from(selectEl.selectedOptions || []).map(o => o.value).filter(Boolean);
      }
      function effectiveFilterValues(selectEl){
        const values = selectedValues(selectEl);
        if (!values.length || values.includes(SELECT_ALL_VALUE)) return [];
        return values.filter(v => v !== SELECT_ALL_VALUE);
      }
      function setSelectedValues(selectEl, values){
        const selectedSet = new Set(values || []);
        if (!selectedSet.size || selectedSet.has(SELECT_ALL_VALUE)) {
          selectedSet.clear();
          selectedSet.add(SELECT_ALL_VALUE);
        } else {
          selectedSet.delete(SELECT_ALL_VALUE);
        }
        Array.from(selectEl.options).forEach(o => { o.selected = selectedSet.has(o.value); });
        if (selectEl._multiApi) selectEl._multiApi.syncFromSelect();
      }
      function createChipContainer(selectEl){
        const container = document.createElement('div');
        container.className = 'chipsRow';
        container.id = selectEl.id + '-chips';
        selectEl.parentElement.appendChild(container);
        return container;
      }
      function populateSelect(selectEl, values){
        selectEl.innerHTML = '';
        const all = document.createElement('option');
        all.value = SELECT_ALL_VALUE;
        all.textContent = SELECT_ALL_LABEL;
        all.selected = true;
        selectEl.appendChild(all);
        values.forEach(v => {
          const o = document.createElement('option');
          o.value = v;
          o.textContent = v;
          selectEl.appendChild(o);
        });
      }
      function setupMultiSelect(selectEl, placeholder){
        selectEl.classList.add('nativeMulti');
        const wrap = document.createElement('div');
        wrap.className = 'multiWrap';
        const btn = document.createElement('button');
        btn.type = 'button';
        btn.className = 'multiBtn';
        const menu = document.createElement('div');
        menu.className = 'multiMenu';
        wrap.appendChild(btn);
        wrap.appendChild(menu);
        selectEl.parentElement.appendChild(wrap);

        function isOpen(){ return menu.classList.contains('open'); }
        function closeMenu(){ menu.classList.remove('open'); }
        function openMenu(){ menu.classList.add('open'); }
        function renderButton(){
          const values = selectedValues(selectEl);
          if (!values.length || values.includes(SELECT_ALL_VALUE)) {
            btn.textContent = SELECT_ALL_LABEL;
            return;
          }
          btn.textContent = 'Selected: ' + values.length;
        }
        function syncFromSelect(){
          const selectedSet = new Set(Array.from(selectEl.selectedOptions || []).map(o => o.value));
          Array.from(menu.querySelectorAll('input[type="checkbox"]')).forEach(ch => {
            ch.checked = selectedSet.has(ch.value);
          });
          renderButton();
        }
        function renderOptions(){
          menu.innerHTML = '';
          Array.from(selectEl.options).forEach(option => {
            const label = document.createElement('label');
            label.className = 'multiOption';
            const checkbox = document.createElement('input');
            checkbox.type = 'checkbox';
            checkbox.value = option.value;
            checkbox.checked = option.selected;
            checkbox.addEventListener('change', () => {
              if (option.value === SELECT_ALL_VALUE) {
                if (checkbox.checked) {
                  setSelectedValues(selectEl, [SELECT_ALL_VALUE]);
                } else {
                  setSelectedValues(selectEl, [SELECT_ALL_VALUE]);
                }
              } else {
                const current = new Set(selectedValues(selectEl).filter(v => v !== SELECT_ALL_VALUE));
                if (checkbox.checked) current.add(option.value);
                else current.delete(option.value);
                if (current.size) setSelectedValues(selectEl, Array.from(current));
                else setSelectedValues(selectEl, [SELECT_ALL_VALUE]);
              }
              renderButton();
              render();
            });
            const text = document.createElement('span');
            text.textContent = option.textContent || option.value;
            label.appendChild(checkbox);
            label.appendChild(text);
            menu.appendChild(label);
          });
          renderButton();
        }

        btn.addEventListener('click', () => {
          if (isOpen()) {
            closeMenu();
          } else {
            openMenu();
          }
        });
        document.addEventListener('click', (e) => {
          if (!wrap.contains(e.target)) closeMenu();
        });

        renderOptions();
        selectEl._multiApi = {
          getValues: () => Array.from(selectEl.selectedOptions || []).map(o => o.value).filter(Boolean),
          clear: () => {
            setSelectedValues(selectEl, [SELECT_ALL_VALUE]);
          },
          syncFromSelect,
        };
      }

      populateSelect(moduleEl, uniq(entries.map(e => e.modulePath)));
      populateSelect(scopeEl, uniq(entries.map(e => e.scope)));
      populateSelect(licEl, uniq(entries.flatMap(e => licenseNames(e))));
      populateSelect(relationEl, uniq(entries.map(e => relationLabel(e))));
      vulnEl.innerHTML = '';
      [
        ['all', '(all)'],
        ['with', 'with vulnerabilities'],
        ['without', 'without vulnerabilities'],
      ].forEach(([value, label]) => {
        const o = document.createElement('option');
        o.value = value;
        o.textContent = label;
        vulnEl.appendChild(o);
      });
      vulnEl.value = 'all';
      populateSelect(vulnSeverityEl, ['critical', 'high', 'medium', 'low', 'unknown']);
      setupMultiSelect(moduleEl, 'Select module(s)…');
      setupMultiSelect(scopeEl, 'Select scope(s)…');
      setupMultiSelect(licEl, 'Select license(s)…');
      setupMultiSelect(relationEl, 'Select relation(s)…');
      setupMultiSelect(vulnSeverityEl, 'Select severity…');
      const moduleChips = createChipContainer(moduleEl);
      const scopeChips = createChipContainer(scopeEl);
      const licChips = createChipContainer(licEl);
      const relationChips = createChipContainer(relationEl);
      const vulnSeverityChips = createChipContainer(vulnSeverityEl);

      function unselectValue(selectEl, value){
        const next = selectedValues(selectEl).filter(v => v !== value);
        setSelectedValues(selectEl, next);
      }
      function renderSelectChips(selectEl, chipsEl){
        chipsEl.innerHTML = '';
        const values = selectedValues(selectEl).filter(v => v !== SELECT_ALL_VALUE);
        values.forEach(value => {
          const chip = document.createElement('span');
          chip.className = 'filterChip';
          chip.appendChild(document.createTextNode(value));
          const btn = document.createElement('button');
          btn.type = 'button';
          btn.textContent = 'x';
          btn.title = 'Remove';
          btn.addEventListener('click', () => {
            unselectValue(selectEl, value);
            render();
          });
          chip.appendChild(btn);
          chipsEl.appendChild(chip);
        });
      }
      function clearAllFilters(){
        searchEl.value = '';
        vulnEl.value = 'all';
        [moduleEl, scopeEl, licEl, relationEl, vulnSeverityEl].forEach(selectEl => {
          if (selectEl._multiApi) {
            selectEl._multiApi.clear();
          } else {
            Array.from(selectEl.options).forEach(o => { o.selected = false; });
          }
        });
        render();
      }

      function render(){
        const q = norm(searchEl.value);
        const mod = effectiveFilterValues(moduleEl);
        const scope = effectiveFilterValues(scopeEl);
        const lic = effectiveFilterValues(licEl);
        const relation = effectiveFilterValues(relationEl);
        const vulnSeverity = effectiveFilterValues(vulnSeverityEl);
        const vulnMode = vulnEl.value || 'all';

        const filtered = entries.filter(e => {
          if (mod.length && !mod.includes(e.modulePath)) return false;
          if (scope.length && !scope.includes(e.scope)) return false;
          if (relation.length && !relation.includes(relationLabel(e))) return false;
          const count = vulnCount(e);
          if (vulnMode === 'with' && count === 0) return false;
          if (vulnMode === 'without' && count > 0) return false;
          if (vulnSeverity.length) {
            const levels = entrySeverities(e);
            if (!vulnSeverity.some(v => levels.includes(v))) return false;
          }
          if (lic.length) {
            const lns = licenseNames(e);
            if (!lic.some(v => lns.includes(v))) return false;
          }
          if (q) {
            const hay = norm(e.modulePath + ' ' + e.scope + ' ' + (e.group||'') + ' ' + e.name + ' ' + (e.version||''));
            if (!hay.includes(q)) return false;
          }
          return true;
        });
        lastFiltered = filtered;
        renderSelectChips(moduleEl, moduleChips);
        renderSelectChips(scopeEl, scopeChips);
        renderSelectChips(licEl, licChips);
        renderSelectChips(relationEl, relationChips);
        renderSelectChips(vulnSeverityEl, vulnSeverityChips);

        const total = filtered.length;
        const ext = filtered.filter(e => e.type === 'EXTERNAL').length;
        const proj = filtered.filter(e => e.type === 'PROJECT').length;
        const vulnDeps = filtered.filter(e => vulnCount(e) > 0).length;
        const totalVulns = filtered.reduce((a,e)=>a+vulnCount(e),0);

        statsEl.innerHTML = '';
        const chips = [
          ['Entries', total],
          ['External', ext],
          ['Project', proj],
          ['Deps with vulns', vulnDeps],
          ['Total vulns', totalVulns],
        ];
        chips.forEach(([k,v]) => {
          const c = document.createElement('div');
          c.className = 'chip';
          c.textContent = k + ': ' + v;
          statsEl.appendChild(c);
        });

        rowsEl.innerHTML = '';
        filtered.forEach(e => {
          const tr = document.createElement('tr');

          const tdModule = document.createElement('td');
          tdModule.className = 'mono nowrap';
          tdModule.textContent = e.modulePath;

          const tdCfg = document.createElement('td');
          tdCfg.className = 'mono nowrap';
          tdCfg.textContent = e.configuration;

          const tdDep = document.createElement('td');
          tdDep.className = 'mono';
          tdDep.textContent = depLabel(e);

          const tdLatest = document.createElement('td');
          tdLatest.className = 'mono nowrap';
          tdLatest.textContent = latestLabel(e);

          const tdType = document.createElement('td');
          tdType.innerHTML = e.type === 'PROJECT'
            ? '<span class="pill">PROJECT</span>'
            : '<span class="pill">EXTERNAL</span>';

          const tdRelation = document.createElement('td');
          tdRelation.innerHTML = e.type === 'PROJECT'
            ? '<span class="pill">project-direct</span>'
            : (e.isTransitive ? '<span class="pill">transitive</span>' : '<span class="pill">direct</span>');

          const tdLic = document.createElement('td');
          (e.licenses && e.licenses.length ? e.licenses : [{name:'(unknown)', url:null}]).forEach(l => {
            const span = document.createElement('span');
            span.className = 'pill';
            const label = l.name || l.url || '(unknown)';
            if (l.url) {
              const a = document.createElement('a');
              a.href = l.url;
              a.target = '_blank';
              a.rel = 'noreferrer';
              a.textContent = label;
              span.appendChild(a);
            } else {
              span.textContent = label;
            }
            tdLic.appendChild(span);
          });

          const tdV = document.createElement('td');
          const vulns = e.vulnerabilities || [];
          if (!vulns.length) {
            tdV.innerHTML = '<span class="ok">No known vulns</span>';
          } else {
            const summary = severitySummary(vulns);
            const head = document.createElement('div');
            head.className = 'bad';
            head.textContent = 'Total: ' + vulns.length;
            tdV.appendChild(head);

            ['critical','high','medium','low','unknown'].forEach(k => {
              if (summary[k] > 0) {
                const chip = document.createElement('span');
                chip.className = 'sev ' + k;
                chip.textContent = k + ': ' + summary[k];
                tdV.appendChild(chip);
              }
            });

            const btn = document.createElement('button');
            btn.type = 'button';
            btn.className = 'btn';
            btn.style.marginTop = '8px';
            btn.textContent = 'Open list';
            btn.addEventListener('click', () => openVulnModal(depLabel(e), vulns));
            tdV.appendChild(document.createElement('div')).appendChild(btn);
          }

          tr.appendChild(tdModule);
          tr.appendChild(tdCfg);
          tr.appendChild(tdDep);
          tr.appendChild(tdLatest);
          tr.appendChild(tdType);
          tr.appendChild(tdRelation);
          tr.appendChild(tdLic);
          tr.appendChild(tdV);
          rowsEl.appendChild(tr);
        });
      }

      function vulnsSummary(vulns){
        const arr = vulns || [];
        if (!arr.length) return 'No known vulns';
        const s = severitySummary(arr);
        const parts = [];
        ['critical','high','medium','low','unknown'].forEach(k => {
          if (s[k] > 0) parts.push(k + ':' + s[k]);
        });
        return 'total:' + arr.length + ' (' + parts.join(', ') + ')';
      }
      function exportRowsForDownload(){
        return lastFiltered.map(e => {
          const vulns = e.vulnerabilities || [];
          const vulnRefs = vulns
            .map(v => {
              const ref = v.reference || '';
              const id = v.id || '';
              if (!id && !ref) return '';
              return ref ? (id + ' (' + ref + ')') : id;
            })
            .filter(Boolean)
            .join('\n');
          return {
            Module: e.modulePath || '',
            Config: e.configuration || '',
            Dependency: depLabel(e),
            LatestVersion: latestLabel(e),
            Type: e.type || '',
            Relation: relationLabel(e),
            Licenses: (e.licenses && e.licenses.length)
              ? e.licenses.map(l => l.name || l.url || '(unknown)').join('; ')
              : '(unknown)',
            Vulnerabilities: vulnsSummary(vulns),
            VulnerabilityIds: vulns.map(v => v.id || '').join('; '),
            VulnerabilityIdsWithLinks: vulnRefs || '-',
            VulnerabilityTitles: vulns.map(v => v.title || '').join('; '),
            VulnerabilityRecommendations: vulns.map(v => v.recommendation || '').filter(Boolean).join('; '),
          };
        });
      }
      function downloadBlob(blob, filename){
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        const now = new Date().toISOString().replaceAll(':', '-');
        a.href = url;
        a.download = filename.replace('{ts}', now);
        document.body.appendChild(a);
        a.click();
        a.remove();
        URL.revokeObjectURL(url);
      }
      function exportFilteredToXlsx(){
        if (!window.XLSX) {
          alert('XLSX library not loaded');
          return;
        }
        const rows = exportRowsForDownload();
        const wb = window.XLSX.utils.book_new();
        const ws = window.XLSX.utils.json_to_sheet(rows);
        window.XLSX.utils.book_append_sheet(wb, ws, 'Dependencies');
        const wbout = window.XLSX.write(wb, { bookType: 'xlsx', type: 'array' });
        downloadBlob(
          new Blob([wbout], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' }),
          'dependency-report-{ts}.xlsx'
        );
      }
      function exportFilteredToPdf(){
        if (!window.jspdf || !window.jspdf.jsPDF) {
          alert('jsPDF library not loaded');
          return;
        }
        const rows = exportRowsForDownload();
        const pdfRows = lastFiltered.map(e => {
          const vulns = e.vulnerabilities || [];
          const vulnLinks = vulns
            .map(v => ({ id: v.id || '', url: v.reference || '' }))
            .filter(v => v.id && v.url);
          return {
            Module: e.modulePath || '',
            Config: e.configuration || '',
            Dependency: depLabel(e),
            LatestVersion: latestLabel(e),
            Type: e.type || '',
            Relation: relationLabel(e),
            Licenses: (e.licenses && e.licenses.length)
              ? e.licenses.map(l => l.name || l.url || '(unknown)').join('; ')
              : '(unknown)',
            Vulnerabilities: vulnsSummary(vulns),
            VulnerabilityIds: vulns.map(v => v.id || '').filter(Boolean),
            VulnerabilityLinks: vulnLinks,
          };
        });
        const doc = new window.jspdf.jsPDF({ orientation: 'landscape', unit: 'pt', format: 'a4' });
        const head = [[
          'Module', 'Config', 'Dependency', 'Latest', 'Type', 'Relation', 'Licenses', 'Vulns', 'Vuln IDs'
        ]];
        const body = pdfRows.map(r => [
          r.Module,
          r.Config,
          r.Dependency,
          r.LatestVersion,
          r.Type,
          r.Relation,
          r.Licenses,
          r.Vulnerabilities,
          r.VulnerabilityIds.length ? r.VulnerabilityIds.join('\n') : '-',
        ]);
        doc.setFontSize(12);
        doc.text('Dependency Report (filtered)', 40, 30);
        if (doc.autoTable) {
          doc.autoTable({
            head,
            body,
            startY: 40,
            styles: { fontSize: 8, cellPadding: 3, overflow: 'linebreak' },
            headStyles: { fillColor: [33, 49, 79] },
            columnStyles: {
              0: { cellWidth: 80 },
              1: { cellWidth: 60 },
              2: { cellWidth: 120 },
              3: { cellWidth: 65 },
              4: { cellWidth: 50 },
              5: { cellWidth: 65 },
              6: { cellWidth: 120 },
              7: { cellWidth: 110 },
              8: { cellWidth: 150 },
            },
            didDrawCell: (data) => {
              if (data.section !== 'body' || data.column.index !== 8) return;
              const row = pdfRows[data.row.index];
              if (!row || !row.VulnerabilityLinks || !row.VulnerabilityLinks.length) return;
              const fontSize = (data.cell.styles && data.cell.styles.fontSize) || 8;
              const lineHeight = fontSize * 1.2;
              const leftPad = (typeof data.cell.padding === 'function') ? data.cell.padding('left') : 3;
              const topPad = (typeof data.cell.padding === 'function') ? data.cell.padding('top') : 3;
              const x = data.cell.x + leftPad;
              const yStart = data.cell.y + topPad + fontSize;
              row.VulnerabilityLinks.forEach((v, index) => {
                const y = yStart + index * lineHeight;
                const w = doc.getTextWidth(v.id);
                if (w > 0) {
                  doc.link(x, y - fontSize, w, lineHeight, { url: v.url });
                }
              });
            },
          });
        }
        const blob = doc.output('blob');
        downloadBlob(blob, 'dependency-report-{ts}.pdf');
      }
      exportXlsxBtn.addEventListener('click', exportFilteredToXlsx);
      exportPdfBtn.addEventListener('click', exportFilteredToPdf);
      clearFiltersBtn.addEventListener('click', clearAllFilters);

      ['input','change'].forEach(ev => {
        searchEl.addEventListener(ev, render);
        vulnEl.addEventListener(ev, render);
      });

      render();
      """.trimIndent()
    )

    return "<!doctype html>\n" + doc.outerHtml()
  }
}

private fun Element.appendFilter(label: String, id: String, type: String, placeholder: String) {
  val row = appendElement("div").addClass("row")
  row.appendElement("label").attr("for", id).text(label)
  row.appendElement("input").attr("id", id).attr("type", type).attr("placeholder", placeholder)
}

private fun Element.appendSelect(label: String, id: String, multiple: Boolean = false) {
  val row = appendElement("div").addClass("row")
  row.appendElement("label").attr("for", id).text(label)
  val select = row.appendElement("select").attr("id", id)
  if (multiple) {
    select.attr("multiple", "multiple")
  }
}

