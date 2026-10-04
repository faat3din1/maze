(function () {
  const core = MazeCore;
  const ICONS = {
    back: '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M15 5 L8 12 L15 19" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>',
    settings: '<svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="3" fill="none" stroke="currentColor" stroke-width="2"/><path d="M12 3.5v2.2M12 18.3v2.2M3.5 12h2.2M18.3 12h2.2M6 6l1.6 1.6M16.4 16.4L18 18M18 6l-1.6 1.6M7.6 16.4L6 18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>',
    share: '<svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="6" cy="12" r="2" fill="none" stroke="currentColor" stroke-width="2"/><circle cx="17" cy="7" r="2" fill="none" stroke="currentColor" stroke-width="2"/><circle cx="17" cy="17" r="2" fill="none" stroke="currentColor" stroke-width="2"/><path d="M8 11.2 L15 8M8 12.8 L15 16" fill="none" stroke="currentColor" stroke-width="2"/></svg>',
    hint: '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M9 18h6M10 21h4" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"/><path d="M8 14a5 5 0 1 1 8 0c-.7.8-1.2 1.5-1.4 2.5H9.4C9.2 15.5 8.7 14.8 8 14z" fill="none" stroke="currentColor" stroke-width="2"/></svg>',
    pause: '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M8 6v12M16 6v12" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>',
    play: '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M8 6 L18 12 L8 18 Z" fill="currentColor"/></svg>',
    restart: '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M20 12a8 8 0 1 1-2.2-5.5" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"/><path d="M20 4v5h-5" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>',
    add: '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 5v14M5 12h14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>',
    check: '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M5 12.5 L10 17.5 L19 7" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>',
  };

  const state = {
    look: "stone",
    screen: "home",
    editorWidth: 8,
    editorHeight: 6,
    editorMaze: null,
    editorTool: "road",
    editorNotes: [],
    editorScale: 1,
    editorOffset: { x: 0, y: 0 },
    showSettings: false,
    shareMaze: null,
    shareNote: null,
    lastSpec: null,
    setup: { algorithm: "dfs", difficulty: "normal", width: 12, height: 10, rivers: 4 },
  };

  const live = { raf: 0, stream: null, scanRaf: 0, tilt: { x: 0, y: 0 }, tiltOn: false, session: null, ro: null, pending: null };

  function el(tag, props, kids) {
    const node = document.createElement(tag);
    if (tag === "button") node.type = "button";
    Object.entries(props || {}).forEach(function (entry) {
      const key = entry[0];
      const value = entry[1];
      if (value == null || value === false) return;
      if (key === "class") node.className = value;
      else if (key === "text") node.textContent = value;
      else if (typeof value === "function" && key.indexOf("on") === 0) node.addEventListener(key.slice(2).toLowerCase(), value);
      else if (key === "hidden") node.hidden = true;
      else node.setAttribute(key, value === true ? "" : value);
    });
    [].concat(kids || []).forEach(function (kid) {
      if (kid == null || kid === false) return;
      node.append(kid.nodeType ? kid : document.createTextNode(String(kid)));
    });
    return node;
  }

  function icon(name) {
    const wrap = el("span", { class: "svg" });
    wrap.innerHTML = ICONS[name];
    return wrap;
  }

  function iconButton(name, label, onClick, extra) {
    const button = el("button", Object.assign({ class: "iconbtn", "aria-label": label, title: label, onclick: onClick }, extra || {}), [icon(name)]);
    return button;
  }

  function wide(text, onClick) {
    return el("button", { class: "wide", onclick: onClick }, [text]);
  }

  function chip(text, selected, onClick, sub) {
    const button = el("button", {
      class: "chip" + (sub ? " algo" : "") + (selected ? " selected" : ""),
      "aria-pressed": String(selected),
      onclick: onClick,
    }, sub ? [el("span", { text: text }), el("span", { class: "chip-sub", text: sub })] : [text]);
    return button;
  }

  function applyLook(look) {
    state.look = look;
    document.documentElement.dataset.look = look;
    const meta = document.querySelector('meta[name="theme-color"]');
    const theme = look === "sketch" ? "#F6F1E4" : look === "pixel" ? "#14182B" : "#1C1915";
    if (meta) meta.content = state.screen === "play" ? "#1C1915" : theme;
  }

  function held() {
    return state.showSettings || state.shareMaze != null;
  }

  function stopCamera() {
    if (live.scanRaf) cancelAnimationFrame(live.scanRaf);
    live.scanRaf = 0;
    if (live.stream) live.stream.getTracks().forEach(function (track) { track.stop(); });
    live.stream = null;
  }

  function stopLoop() {
    if (live.raf) cancelAnimationFrame(live.raf);
    live.raf = 0;
    if (live.ro) live.ro.disconnect();
    live.ro = null;
    if (live.session && live.session.helpTimer) clearTimeout(live.session.helpTimer);
    live.session = null;
  }

  function render() {
    stopCamera();
    stopLoop();
    const app = document.getElementById("app");
    const screen = state.screen === "home" ? renderHome()
      : state.screen === "setup" ? renderSetup()
      : state.screen === "play" ? renderPlay()
      : state.screen === "editor" ? renderEditor()
      : state.screen === "scan" ? renderScan()
      : renderScanResult();
    app.replaceChildren(screen, el("div", { id: "modal" }));
    renderModal();
    applyLook(state.look);
    if (state.screen === "play") beginPlay();
    if (state.screen === "editor") bindEditor();
    if (state.screen === "scan") startCamera();
  }

  function showScreen(name) {
    state.screen = name;
    render();
  }

  function back() {
    if (state.showSettings) { state.showSettings = false; renderModal(); return; }
    if (state.shareMaze) { state.shareMaze = null; state.shareNote = null; renderModal(); return; }
    if (state.screen === "home") return;
    if (state.screen === "play" && live.pending && live.pending.backToEditor) showScreen("editor");
    else showScreen("home");
  }

  function renderHome() {
    const file = el("input", { type: "file", accept: "image/*", hidden: true, "aria-hidden": "true" });
    file.addEventListener("change", function () {
      const picked = file.files && file.files[0];
      file.value = "";
      if (!picked) return;
      decodeImage(picked).then(showScanResult);
    });
    let paste = "";
    const area = el("textarea", { id: "paste", "aria-label": "貼上迷宮文字" });
    area.addEventListener("input", function () { paste = area.value; });
    return el("main", { class: "page" }, [
      el("div", { class: "column" }, [
        el("div", { class: "title-row" }, [
          el("h1", { text: "便便滾滾" }),
          iconButton("settings", "設定", openSettings),
        ]),
        el("p", { text: "傾斜手機，讓便便滾到出口。沒有感應器時可以用手指拖曳。" }),
        wide("開始", function () { showScreen("setup"); }),
        wide("編輯地圖", function () { showScreen("editor"); }),
        wide("掃描 QR Code", function () { showScreen("scan"); }),
        wide("從圖片讀取", function () { file.click(); }),
        el("label", { class: "field" }, [el("span", { text: "貼上迷宮文字" }), area]),
        wide("用文字加入", function () { showScanResult(core.decodeShareText(paste)); }),
        file,
      ]),
    ]);
  }

  function renderSetup() {
    const setup = state.setup;
    const column = el("div", { class: "column" });
    const blurb = el("p", { id: "blurb", text: core.levelBlurb(setup.difficulty, setup.width, setup.height, setup.rivers) });
    const custom = el("div", { id: "custom", class: "field" });
    if (setup.difficulty !== "custom") custom.hidden = true;
    custom.append(
      slider("寬", setup.width, core.MIN_WIDTH, core.MAX_WIDTH, function (value) { setup.width = value; refreshBlurb(); }),
      slider("高", setup.height, core.MIN_HEIGHT, core.MAX_HEIGHT, function (value) { setup.height = value; refreshBlurb(); }),
      slider("河流", setup.rivers, 0, 20, function (value) { setup.rivers = value; refreshBlurb(); })
    );
    function refreshBlurb() {
      blurb.textContent = core.levelBlurb(setup.difficulty, setup.width, setup.height, setup.rivers);
      custom.hidden = setup.difficulty !== "custom";
    }
    function algorithmRow() {
      return el("div", { class: "chips", id: "algos" }, [
        ["dfs", "DFS", "遞迴回溯"],
        ["kruskal", "Kruskal", "岔路較均勻"],
        ["prim", "Prim", "死路較多"],
      ].map(function (item) {
        return chip(item[1], setup.algorithm === item[0], function () {
          setup.algorithm = item[0];
          column.querySelectorAll("#algos .chip").forEach(function (button, index) {
            const on = ["dfs", "kruskal", "prim"][index] === setup.algorithm;
            button.classList.toggle("selected", on);
            button.setAttribute("aria-pressed", String(on));
          });
        }, item[2]);
      }));
    }
    function difficultyRow() {
      return el("div", { class: "chips", id: "diffs" }, [
        ["easy", "易"],
        ["normal", "普通"],
        ["hard", "難"],
        ["custom", "自訂"],
      ].map(function (item) {
        return chip(item[1], setup.difficulty === item[0], function () {
          setup.difficulty = item[0];
          column.querySelectorAll("#diffs .chip").forEach(function (button, index) {
            const on = ["easy", "normal", "hard", "custom"][index] === setup.difficulty;
            button.classList.toggle("selected", on);
            button.setAttribute("aria-pressed", String(on));
          });
          refreshBlurb();
        });
      }));
    }
    column.append(
      iconButton("back", "返回", back),
      el("h2", { text: "開局" }),
      el("p", { text: "演算法" }),
      algorithmRow(),
      el("p", { text: "難度" }),
      difficultyRow(),
      blurb,
      custom,
      wide("生成並開始", function () {
        const level = core.levelSpec(setup.difficulty, setup.width, setup.height, setup.rivers);
        state.lastSpec = {
          algorithm: setup.algorithm,
          difficulty: setup.difficulty,
          width: level.width,
          height: level.height,
          rivers: level.rivers,
          loops: level.loops,
        };
        const maze = core.generateMaze(state.lastSpec.algorithm, state.lastSpec.width, state.lastSpec.height, state.lastSpec.rivers, state.lastSpec.loops);
        enableTilt();
        startPlay(maze, false);
      })
    );
    return el("main", { class: "page setup" }, [column]);
  }

  function slider(label, value, min, max, onChange) {
    const text = el("span", { text: label + " " + value });
    const input = el("input", { type: "range", min: String(min), max: String(max), step: "1", value: String(value), "aria-label": label });
    input.addEventListener("input", function () {
      const next = Number(input.value);
      text.textContent = label + " " + next;
      onChange(next);
    });
    return el("label", { class: "field" }, [text, input]);
  }

  function renderPlay() {
    return el("main", { class: "play" }, [
      el("div", { class: "bar" }, [
        iconButton("back", "返回", back),
        el("span", { class: "spacer" }),
        iconButton("share", "分享", function () { openShare(live.pending.maze, shareText()); }),
        iconButton("hint", "提示", toggleHint, { id: "hint-btn" }),
        iconButton("settings", "設定", openSettings),
        iconButton("pause", "暫停", togglePause, { id: "pause-btn" }),
        iconButton("restart", "重開", restartRun),
      ]),
      el("div", { class: "stage", id: "stage" }, [
        el("canvas", { id: "board" }),
        el("div", { id: "splash", class: "splash", hidden: true, text: "被冲走了，回到入口" }),
        el("div", { id: "play-overlay", class: "overlay", hidden: true }),
      ]),
      el("div", { class: "hud" }, [
        el("p", { id: "hud-help", text: "傾斜手機，讓便便滾到出口。或用手指拖曳。" }),
        el("div", { id: "hud-meta", hidden: true }, [
          el("span", { id: "hud-size" }),
          el("time", { id: "hud-time" }),
        ]),
      ]),
    ]);
  }

  function beginPlay() {
    const pending = live.pending;
    const maze = pending.maze;
    const session = {
      maze: maze,
      sim: new MazePhysics.MarbleSim(maze),
      solution: core.solutionPath(maze),
      showHint: false,
      usedHint: false,
      showHelp: true,
      elapsed: 0,
      paused: false,
      splash: 0,
      seenRiver: 0,
      dragging: false,
      dragX: 0,
      dragY: 0,
      lastTime: 0,
      shownWin: false,
      pauseShown: false,
      canRegenerate: state.lastSpec != null,
    };
    live.session = session;
    const size = document.getElementById("hud-size");
    if (size) size.textContent = maze.width + "×" + maze.height;
    const hint = document.getElementById("hint-btn");
    if (hint) hint.disabled = session.solution == null;
    session.helpTimer = setTimeout(function () {
      session.showHelp = false;
      const help = document.getElementById("hud-help");
      const meta = document.getElementById("hud-meta");
      if (help) help.hidden = true;
      if (meta) meta.hidden = false;
    }, 2000);
    const canvas = document.getElementById("board");
    let last = null;
    canvas.addEventListener("pointerdown", function (event) {
      if (event.button !== 0) return;
      try { canvas.setPointerCapture(event.pointerId); } catch (err) {}
      session.dragging = true;
      last = { x: event.clientX, y: event.clientY };
      session.dragX = 0;
      session.dragY = 0;
    });
    canvas.addEventListener("pointermove", function (event) {
      if (!session.dragging || !last) return;
      const dx = event.clientX - last.x;
      const dy = event.clientY - last.y;
      last = { x: event.clientX, y: event.clientY };
      const dest = MazeDraw.fitBoard(canvas.clientWidth, canvas.clientHeight, maze.width, maze.height);
      const cell = Math.max(1, dest.width / maze.width);
      session.dragX = dx / cell * 48;
      session.dragY = dy / cell * 48;
    });
    function endDrag() { session.dragging = false; }
    canvas.addEventListener("pointerup", endDrag);
    canvas.addEventListener("pointercancel", endDrag);
    canvas.addEventListener("touchmove", function (event) { event.preventDefault(); }, { passive: false });
    function frame(now) {
      if (live.session !== session) return;
      const dt = session.lastTime ? Math.min(0.05, (now - session.lastTime) / 1000) : 0;
      session.lastTime = now;
      const active = !session.paused && !held() && !session.sim.won;
      if (active) {
        session.elapsed += dt;
        session.sim.ax = live.tilt.x * 22 + (session.dragging ? session.dragX : 0);
        session.sim.ay = live.tilt.y * 22 + (session.dragging ? session.dragY : 0);
        session.sim.step(dt);
        if (session.sim.riverFlash !== session.seenRiver) {
          session.seenRiver = session.sim.riverFlash;
          session.splash = 1;
        } else session.splash = Math.max(0, session.splash - dt * 1.4);
      }
      drawPlay();
      const time = document.getElementById("hud-time");
      if (time) time.textContent = formatTime(session.elapsed);
      const splash = document.getElementById("splash");
      if (splash) {
        const show = session.splash > 0 && !session.sim.won;
        splash.hidden = !show;
        if (show) splash.style.opacity = String(Math.min(1, Math.max(0.2, session.splash)));
      }
      if (session.sim.won && !session.shownWin) {
        session.shownWin = true;
        showWin();
      }
      live.raf = requestAnimationFrame(frame);
    }
    live.raf = requestAnimationFrame(frame);
  }

  function drawPlay() {
    const session = live.session;
    const canvas = document.getElementById("board");
    if (!session || !canvas || !canvas.clientWidth || !canvas.clientHeight) return;
    const view = prep(canvas);
    const dest = MazeDraw.fitBoard(view.w, view.h, session.maze.width, session.maze.height);
    let hint = null;
    if (session.showHint) {
      const column = clamp(Math.floor(session.sim.x), 0, session.maze.width - 1);
      const row = clamp(Math.floor(session.sim.y), 0, session.maze.height - 1);
      hint = core.solutionPath(session.maze, row * session.maze.width + column);
    }
    MazeDraw.drawMazeScene(view.ctx, session.maze, state.look, dest, session.sim.x, session.sim.y, session.sim.radius, hint);
  }

  function prep(canvas, scale, offset) {
    const dpr = Math.min(window.devicePixelRatio || 1, 3);
    const w = canvas.clientWidth;
    const h = canvas.clientHeight;
    const pw = Math.max(1, Math.round(w * dpr));
    const ph = Math.max(1, Math.round(h * dpr));
    if (canvas.width !== pw || canvas.height !== ph) {
      canvas.width = pw;
      canvas.height = ph;
    }
    const ctx = canvas.getContext("2d");
    ctx.setTransform(1, 0, 0, 1, 0, 0);
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    const zoom = scale || 1;
    const pan = offset || { x: 0, y: 0 };
    ctx.setTransform(dpr * zoom, 0, 0, dpr * zoom, dpr * (w / 2 * (1 - zoom) + pan.x), dpr * (h / 2 * (1 - zoom) + pan.y));
    return { ctx: ctx, w: w, h: h };
  }

  function updatePlayChrome() {
    const session = live.session;
    if (!session) return;
    const pause = document.getElementById("pause-btn");
    if (pause) {
      pause.setAttribute("aria-label", session.paused ? "繼續" : "暫停");
      pause.title = session.paused ? "繼續" : "暫停";
      pause.querySelector(".svg").innerHTML = ICONS[session.paused ? "play" : "pause"];
    }
    const hint = document.getElementById("hint-btn");
    if (hint) hint.classList.toggle("selected", session.showHint);
  }

  function toggleHint() {
    const session = live.session;
    if (!session || !session.solution) return;
    if (!session.showHint) session.usedHint = true;
    session.showHint = !session.showHint;
    updatePlayChrome();
  }

  function togglePause() {
    const session = live.session;
    if (!session || session.sim.won) return;
    session.paused = !session.paused;
    session.pauseShown = session.paused;
    if (session.paused) showPause();
    else hideOverlay();
    updatePlayChrome();
  }

  function restartRun() {
    const session = live.session;
    if (!session) return;
    session.sim.reset();
    session.splash = 0;
    session.paused = false;
    session.elapsed = 0;
    session.usedHint = false;
    session.showHint = false;
    session.seenRiver = session.sim.riverFlash;
    session.shownWin = false;
    session.pauseShown = false;
    hideOverlay();
    updatePlayChrome();
  }

  function hideOverlay() {
    const overlay = document.getElementById("play-overlay");
    if (!overlay) return;
    overlay.hidden = true;
    overlay.replaceChildren();
  }

  function showPause() {
    const overlay = document.getElementById("play-overlay");
    if (!overlay) return;
    overlay.hidden = false;
    overlay.replaceChildren(el("div", { class: "card" }, [
      el("h2", { text: "暫停" }),
      wide("繼續", function () { togglePause(); }),
    ]));
  }

  function showWin() {
    const session = live.session;
    const overlay = document.getElementById("play-overlay");
    if (!session || !overlay) return;
    const actions = [
      wide("再玩一次", restartRun),
      session.canRegenerate ? wide("同難度新地圖", function () { regenerate(false); }) : null,
      session.canRegenerate ? wide("升級難度", function () { regenerate(true); }) : null,
      wide("分享", function () { openShare(session.maze, shareText()); }),
      wide("返回", back),
    ];
    overlay.hidden = false;
    overlay.replaceChildren(el("div", { class: "card" }, [
      el("h2", { text: "過關" }),
      el("p", { id: "win-time", text: formatTime(session.elapsed) }),
      el("div", { class: "actions" }, actions),
    ]));
  }

  function shareText() {
    const session = live.session;
    if (!session || !session.sim.won) return null;
    const total = Math.max(0, Math.trunc(session.elapsed));
    const clock = Math.floor(total / 60) + "分 " + String(total % 60).padStart(2, "0") + " 秒";
    const line = "我用了 " + clock + " 通過了這關";
    return session.usedHint ? line + "（開了hints）" : line;
  }

  function formatTime(seconds) {
    const total = Math.max(0, Math.trunc(seconds));
    return Math.floor(total / 60) + ":" + String(total % 60).padStart(2, "0");
  }

  function regenerate(harder) {
    if (!state.lastSpec) return;
    const next = harder ? core.upgrade(state.lastSpec) : state.lastSpec;
    state.lastSpec = next;
    startPlay(core.generateMaze(next.algorithm, next.width, next.height, next.rivers, next.loops), false);
  }

  function startPlay(maze, backToEditor) {
    live.pending = { maze: maze, backToEditor: backToEditor };
    state.screen = "play";
    render();
  }

  function renderEditor() {
    const maze = state.editorMaze;
    const stageKids = maze ? [
      el("canvas", { id: "board" }),
      el("p", { id: "zoom-label", class: "zoom", text: zoomText() }),
      el("div", { id: "notes", class: "notes" }),
    ] : [el("p", { class: "empty", text: "先設定大小，再按建立。" })];
    const tools = [
      ["road", "道路", "#F4F1EA"],
      ["entrance", "入口", "#9CCC65"],
      ["exit", "出口", "#FFD54F"],
      ["river", "河流", "#4FC3F7"],
      ["wall", "障礙物", "#4E342E"],
    ].map(function (item) {
      const button = el("button", {
        class: "tool" + (state.editorTool === item[0] ? " selected" : ""),
        "data-tool": item[0],
        onclick: function () {
          state.editorTool = item[0];
          document.querySelectorAll(".tool").forEach(function (node) {
            node.classList.toggle("selected", node.dataset.tool === state.editorTool);
          });
        },
      }, [el("span", { class: "swatch" }), item[1]]);
      button.querySelector(".swatch").style.background = item[2];
      return button;
    });
    return el("main", { class: "editor" }, [
      el("div", { class: "bar" }, [
        iconButton("back", "返回", back),
        el("div", { class: "bar-scroll" }, [
          stepper("寬", "editorWidth", core.MIN_WIDTH, core.MAX_WIDTH),
          stepper("高", "editorHeight", core.MIN_HEIGHT, core.MAX_HEIGHT),
        ]),
        iconButton("add", "建立 " + state.editorWidth + "×" + state.editorHeight, createBlank, { id: "create-btn" }),
        iconButton("settings", "設定", openSettings),
      ]),
      el("div", { class: "stage", id: "stage" }, stageKids),
      el("div", { class: "tools" }, tools),
      el("div", { class: "icon-scroll" }, [
        iconButton("check", "驗證", function () { checkEditor(); }, { id: "check-btn" }),
        iconButton("play", "試玩", function () { checkEditor(function (next) { state.lastSpec = null; enableTilt(); startPlay(next, true); }); }, { id: "try-btn" }),
        iconButton("share", "分享", function () { checkEditor(function (next) { openShare(next, null); }); }, { id: "share-btn" }),
      ]),
    ]);
  }

  function bindEditor() {
    ["check-btn", "try-btn", "share-btn"].forEach(function (id) {
      const button = document.getElementById(id);
      if (button) button.disabled = !state.editorMaze;
    });
    const canvas = document.getElementById("board");
    const notes = document.getElementById("notes");
    if (!canvas || !state.editorMaze) return;
    fillNotes(notes);
    const pointers = new Map();
    let multi = false;
    let strokeKey = null;
    function localPoint(event) {
      const rect = canvas.getBoundingClientRect();
      return {
        x: (event.clientX - rect.left) * (canvas.clientWidth / rect.width),
        y: (event.clientY - rect.top) * (canvas.clientHeight / rect.height),
      };
    }
    function targetKey(pos) {
      const grid = viewToGrid(pos);
      if (!grid) return null;
      const c = Math.floor(grid.gx);
      const r = Math.floor(grid.gy);
      if (state.editorTool !== "wall") return { grid: grid, key: state.editorTool + ":" + c + ":" + r };
      const fx = grid.gx - c;
      const fy = grid.gy - r;
      const options = [["up", fy], ["down", 1 - fy], ["left", fx], ["right", 1 - fx]];
      options.sort(function (a, b) { return a[1] - b[1]; });
      return { grid: grid, key: "wall:" + c + ":" + r + ":" + options[0][0] };
    }
    function paint(pos) {
      if (!state.editorMaze) return;
      const target = targetKey(pos);
      if (!target || target.key === strokeKey) return;
      const next = core.editAt(state.editorMaze, state.editorTool, target.grid.gx, target.grid.gy);
      if (!next) return;
      strokeKey = target.key;
      state.editorMaze = next;
      state.editorNotes = [];
      fillNotes(notes);
      drawEditor();
    }
    function viewToGrid(pos) {
      const w = canvas.clientWidth;
      const h = canvas.clientHeight;
      const dest = MazeDraw.fitBoard(w, h, state.editorMaze.width, state.editorMaze.height);
      const localX = w / 2 + (pos.x - state.editorOffset.x - w / 2) / state.editorScale;
      const localY = h / 2 + (pos.y - state.editorOffset.y - h / 2) / state.editorScale;
      return { gx: (localX - dest.left) / (dest.width / state.editorMaze.width), gy: (localY - dest.top) / (dest.height / state.editorMaze.height) };
    }
    canvas.addEventListener("pointerdown", function (event) {
      if (event.button === 1 || event.altKey) {
        pointers.set(event.pointerId, { point: localPoint(event), pan: true });
        try { canvas.setPointerCapture(event.pointerId); } catch (err) {}
        return;
      }
      if (event.button !== 0) return;
      try { canvas.setPointerCapture(event.pointerId); } catch (err) {}
      pointers.set(event.pointerId, { point: localPoint(event), pan: false });
      if (pointers.size >= 2) multi = true;
      else paint(localPoint(event));
    });
    canvas.addEventListener("pointermove", function (event) {
      if (!pointers.has(event.pointerId)) return;
      const previous = pointers.get(event.pointerId);
      const next = localPoint(event);
      if (previous.pan) {
        onTransform(1, { x: next.x - previous.point.x, y: next.y - previous.point.y });
        pointers.set(event.pointerId, { point: next, pan: true });
        return;
      }
      const ids = Array.from(pointers.keys()).filter(function (id) { return !pointers.get(id).pan; });
      if (ids.length >= 2) {
        multi = true;
        const oldA = pointers.get(ids[0]).point;
        const oldB = pointers.get(ids[1]).point;
        pointers.set(event.pointerId, { point: next, pan: false });
        const newA = pointers.get(ids[0]).point;
        const newB = pointers.get(ids[1]).point;
        const oldLen = Math.hypot(oldA.x - oldB.x, oldA.y - oldB.y) || 1;
        const newLen = Math.hypot(newA.x - newB.x, newA.y - newB.y) || 1;
        onTransform(newLen / oldLen, {
          x: (newA.x + newB.x) / 2 - (oldA.x + oldB.x) / 2,
          y: (newA.y + newB.y) / 2 - (oldA.y + oldB.y) / 2,
        });
        return;
      }
      pointers.set(event.pointerId, { point: next, pan: false });
      if (!multi) paint(next);
    });
    function endPointer(event) {
      pointers.delete(event.pointerId);
      if (pointers.size === 0) {
        multi = false;
        strokeKey = null;
      }
    }
    canvas.addEventListener("pointerup", endPointer);
    canvas.addEventListener("pointercancel", endPointer);
    canvas.addEventListener("wheel", function (event) {
      event.preventDefault();
      onTransform(Math.exp(-event.deltaY * 0.0015), { x: 0, y: 0 });
    }, { passive: false });
    canvas.addEventListener("touchmove", function (event) { event.preventDefault(); }, { passive: false });
    canvas.addEventListener("contextmenu", function (event) { event.preventDefault(); });
    if (live.ro) live.ro.disconnect();
    live.ro = new ResizeObserver(function () { drawEditor(); });
    live.ro.observe(canvas);
    requestAnimationFrame(drawEditor);
  }

  function onTransform(zoom, pan) {
    let next = clamp(state.editorScale * zoom, 1, 5);
    if (next <= 1.01) {
      state.editorScale = 1;
      state.editorOffset = { x: 0, y: 0 };
    } else {
      state.editorScale = next;
      const limit = 800 * next;
      state.editorOffset = {
        x: clamp(state.editorOffset.x + pan.x, -limit, limit),
        y: clamp(state.editorOffset.y + pan.y, -limit, limit),
      };
    }
    const label = document.getElementById("zoom-label");
    if (label) label.textContent = zoomText();
    drawEditor();
  }

  function zoomText() {
    return Math.round(state.editorScale * 100) + "%  ·  單指塗色，雙指縮放同移動";
  }

  function drawEditor() {
    const canvas = document.getElementById("board");
    if (!canvas || !state.editorMaze || !canvas.clientWidth) return;
    const view = prep(canvas, state.editorScale, state.editorOffset);
    const dest = MazeDraw.fitBoard(view.w, view.h, state.editorMaze.width, state.editorMaze.height);
    MazeDraw.drawMazeScene(view.ctx, state.editorMaze, "editor", dest, null, null, 0.2, null);
  }

  function fillNotes(host) {
    if (!host) return;
    host.replaceChildren();
    host.hidden = state.editorNotes.length === 0;
    state.editorNotes.forEach(function (note) { host.append(el("p", { text: note })); });
  }

  function stepper(label, key, min, max) {
    const value = el("span", { class: "step-val", text: String(state[key]) });
    function can(delta) {
      return delta < 0 ? state[key] > min : state[key] < max;
    }
    function change(delta) {
      if (!can(delta)) return;
      state[key] = clamp(state[key] + delta, min, max);
      value.textContent = String(state[key]);
      minus.disabled = !can(-1);
      plus.disabled = !can(1);
      const create = document.getElementById("create-btn");
      if (create) {
        const caption = "建立 " + state.editorWidth + "×" + state.editorHeight;
        create.setAttribute("aria-label", caption);
        create.title = caption;
      }
    }
    const minus = holdStep("－", function () { return can(-1); }, function () { change(-1); });
    const plus = holdStep("＋", function () { return can(1); }, function () { change(1); });
    minus.disabled = !can(-1);
    plus.disabled = !can(1);
    return el("div", { class: "stepper" }, [el("span", { text: label }), minus, value, plus]);
  }

  function holdStep(label, canStep, onStep) {
    const button = el("button", { class: "step", text: label });
    let timer = null;
    let repeating = false;
    function stop(commitTap) {
      clearTimeout(timer);
      timer = null;
      if (commitTap && !repeating && canStep()) onStep();
      repeating = false;
    }
    button.addEventListener("pointerdown", function (event) {
      if (!canStep()) return;
      event.preventDefault();
      repeating = false;
      button.setPointerCapture(event.pointerId);
      timer = setTimeout(function tick() {
        if (!canStep()) { stop(false); return; }
        repeating = true;
        onStep();
        timer = setTimeout(tick, 70);
      }, 380);
    });
    button.addEventListener("pointerup", function () { stop(true); });
    button.addEventListener("pointercancel", function () { stop(false); });
    button.addEventListener("contextmenu", function (event) { event.preventDefault(); });
    return button;
  }

  function createBlank() {
    state.editorMaze = core.sealBorder(core.blank(state.editorWidth, state.editorHeight));
    state.editorNotes = [];
    state.editorScale = 1;
    state.editorOffset = { x: 0, y: 0 };
    showScreen("editor");
  }

  function checkEditor(then) {
    if (!state.editorMaze) return;
    const sealed = core.sealBorder(state.editorMaze);
    state.editorMaze = sealed;
    const errors = core.validate(sealed);
    state.editorNotes = errors.length ? errors : ["驗證通過"];
    fillNotes(document.getElementById("notes"));
    drawEditor();
    if (!errors.length && then) then(sealed);
  }

  function renderScan() {
    const video = el("video", { id: "camera", autoplay: true, muted: true, playsinline: true });
    video.muted = true;
    video.playsInline = true;
    return el("main", { class: "scan" }, [
      el("div", { class: "bar" }, [iconButton("back", "返回", back)]),
      video,
      el("p", { text: "掃描迷宮 QR Code" }),
    ]);
  }

  function startCamera() {
    const video = document.getElementById("camera");
    if (!video || !navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
      showScanResult(core.fail(["沒有掃到 QR"]));
      return;
    }
    navigator.mediaDevices.getUserMedia({ video: { facingMode: { ideal: "environment" } }, audio: false }).then(function (stream) {
      if (state.screen !== "scan") {
        stream.getTracks().forEach(function (track) { track.stop(); });
        return;
      }
      live.stream = stream;
      video.srcObject = stream;
      return video.play();
    }).then(function () {
      if (state.screen !== "scan") return;
      const canvas = document.createElement("canvas");
      const ctx = canvas.getContext("2d", { willReadFrequently: true });
      let done = false;
      const tick = function () {
        if (done || state.screen !== "scan") return;
        if (video.readyState >= 2 && video.videoWidth) {
          const scale = Math.min(1, 720 / video.videoWidth);
          canvas.width = Math.max(1, Math.round(video.videoWidth * scale));
          canvas.height = Math.max(1, Math.round(video.videoHeight * scale));
          ctx.drawImage(video, 0, 0, canvas.width, canvas.height);
          const code = jsQR(ctx.getImageData(0, 0, canvas.width, canvas.height).data, canvas.width, canvas.height);
          if (code) {
            done = true;
            showScanResult(outcomeFromCode(code));
            return;
          }
        }
        live.scanRaf = requestAnimationFrame(tick);
      };
      live.scanRaf = requestAnimationFrame(tick);
    }).catch(function () {
      if (state.screen === "scan") showScanResult(core.fail(["沒有掃到 QR"]));
    });
  }

  function decodeImage(file) {
    return new Promise(function (resolve) {
      const url = URL.createObjectURL(file);
      const image = new Image();
      image.onload = function () {
        URL.revokeObjectURL(url);
        let width = image.naturalWidth;
        let height = image.naturalHeight;
        if (!width || !height) { resolve(core.fail(["讀不到圖片"])); return; }
        const scale = Math.min(1, 2048 / Math.max(width, height));
        width = Math.max(1, Math.round(width * scale));
        height = Math.max(1, Math.round(height * scale));
        const canvas = document.createElement("canvas");
        canvas.width = width;
        canvas.height = height;
        const ctx = canvas.getContext("2d", { willReadFrequently: true });
        ctx.drawImage(image, 0, 0, width, height);
        const code = jsQR(ctx.getImageData(0, 0, width, height).data, width, height, { inversionAttempts: "attemptBoth" });
        resolve(code ? outcomeFromCode(code) : core.fail(["沒有掃到 QR"]));
      };
      image.onerror = function () {
        URL.revokeObjectURL(url);
        resolve(core.fail(["讀不到圖片"]));
      };
      image.src = url;
    });
  }

  function outcomeFromCode(code) {
    if (code.binaryData) {
      const binary = core.decode(code.binaryData);
      if (binary.ok) return binary;
    }
    if (code.data) return core.decodeQrText(code.data);
    return core.fail(["沒有掃到 QR"]);
  }

  function showScanResult(outcome) {
    state.scanOutcome = outcome;
    showScreen("result");
  }

  function renderScanResult() {
    const outcome = state.scanOutcome || core.fail(["沒有掃到 QR"]);
    const body = outcome.ok ? [
      el("h2", { text: "掃描成功" }),
      el("p", { text: "地圖 " + outcome.maze.width + "×" + outcome.maze.height }),
      wide("開始遊戲", function () {
        state.lastSpec = null;
        enableTilt();
        startPlay(outcome.maze, false);
      }),
    ] : [
      el("h2", { text: "這張圖不能玩" }),
      el("ul", { class: "reasons" }, outcome.reasons.map(function (reason) { return el("li", { text: "・ " + reason }); })),
    ];
    return el("main", { class: "page" }, [
      el("div", { class: "column" }, [el("button", { class: "textbtn", onclick: back, text: "返回" })].concat(body)),
    ]);
  }

  function openSettings() {
    state.showSettings = true;
    renderModal();
  }

  function openShare(maze, note) {
    state.shareMaze = maze;
    state.shareNote = note;
    renderModal();
  }

  function renderModal() {
    const host = document.getElementById("modal");
    if (!host) return;
    host.replaceChildren();
    if (state.showSettings) host.append(settingsDialog());
    else if (state.shareMaze) host.append(shareDialog(state.shareMaze, state.shareNote));
    document.body.classList.toggle("lock", state.showSettings || state.shareMaze != null);
  }

  function settingsDialog() {
    const choices = [["stone", "石牆"], ["sketch", "素描"], ["pixel", "像素"]];
    return el("div", { class: "backdrop", onclick: function (event) { if (event.target === event.currentTarget) back(); } }, [
      el("div", { class: "dialog", role: "dialog", "aria-modal": "true", "aria-label": "設定" }, [
        el("h2", { text: "設定" }),
        el("p", { text: "主題" }),
        el("div", { class: "chips" }, choices.map(function (item) {
          const button = chip(item[1], state.look === item[0], function () {
            applyLook(item[0]);
            document.querySelectorAll("[data-look-choice]").forEach(function (node) {
              const on = node.dataset.lookChoice === state.look;
              node.classList.toggle("selected", on);
              node.setAttribute("aria-pressed", String(on));
            });
          });
          button.dataset.lookChoice = item[0];
          return button;
        })),
        el("p", { class: "small", text: "主題會改首頁、開局和編輯器外框。迷宮格子和遊戲畫面維持原樣。" }),
        el("div", { class: "dialog-actions" }, [el("button", { class: "textbtn", onclick: back, text: "關閉" })]),
      ]),
    ]);
  }

  function shareDialog(maze, note) {
    let picture = null;
    try { picture = qrPicture(core.encode(maze)); } catch (err) { picture = el("p", { text: "QR 產生失敗" }); }
    const copy = el("button", { class: "textbtn", text: "複製文字" });
    copy.addEventListener("click", function () {
      const payload = core.bytesToBase64(core.encode(maze));
      const done = function () { copy.textContent = "已複製"; };
      if (navigator.clipboard && navigator.clipboard.writeText) navigator.clipboard.writeText(payload).then(done).catch(function () { fallbackCopy(payload); done(); });
      else { fallbackCopy(payload); done(); }
    });
    return el("div", { class: "backdrop", onclick: function (event) { if (event.target === event.currentTarget) back(); } }, [
      el("div", { class: "dialog", role: "dialog", "aria-modal": "true", "aria-label": "分享" }, [
        el("h2", { text: "分享" + maze.width + "×" + maze.height + "迷宮" }),
        note ? el("p", { text: note }) : null,
        picture,
        el("p", { class: "small", text: "截圖分享 | APP內掃描加入迷宮" }),
        el("div", { class: "dialog-actions" }, [
          copy,
          el("button", { class: "textbtn", onclick: back, text: "關閉" }),
        ]),
      ]),
    ]);
  }

  function qrPicture(bytes) {
    const qr = qrcode(0, "L");
    qr.addData(core.bytesToLatin1(bytes), "Byte");
    qr.make();
    const count = qr.getModuleCount();
    const margin = 1;
    const scale = 8;
    const canvas = el("canvas", { class: "qr", role: "img", "aria-label": "便便滾滾 QR Code" });
    const size = (count + margin * 2) * scale;
    canvas.width = size;
    canvas.height = size;
    const ctx = canvas.getContext("2d");
    ctx.fillStyle = "#fff";
    ctx.fillRect(0, 0, size, size);
    ctx.fillStyle = "#000";
    for (let r = 0; r < count; r += 1) {
      for (let c = 0; c < count; c += 1) {
        if (qr.isDark(r, c)) ctx.fillRect((c + margin) * scale, (r + margin) * scale, scale, scale);
      }
    }
    return canvas;
  }

  function fallbackCopy(text) {
    const area = el("textarea", { text: text });
    document.body.append(area);
    area.select();
    document.execCommand("copy");
    area.remove();
  }

  function enableTilt() {
    if (live.tiltOn) return;
    const request = window.DeviceOrientationEvent && DeviceOrientationEvent.requestPermission;
    if (typeof request === "function") {
      DeviceOrientationEvent.requestPermission().then(function (answer) {
        if (answer === "granted") listenTilt();
      }).catch(function () {});
      return;
    }
    listenTilt();
  }

  function listenTilt() {
    if (live.tiltOn) return;
    window.addEventListener("deviceorientation", onTilt);
    live.tiltOn = true;
  }

  function onTilt(event) {
    if (event.gamma == null || event.beta == null) return;
    const rad = Math.PI / 180;
    let x = Math.sin(event.gamma * rad);
    let y = Math.sin(event.beta * rad);
    const angle = screenAngle();
    if (angle === 90) { const swap = x; x = -y; y = swap; }
    else if (angle === 270) { const swap = x; x = y; y = -swap; }
    else if (angle === 180) { x = -x; y = -y; }
    live.tilt.x = live.tilt.x * 0.7 + x * 0.3;
    live.tilt.y = live.tilt.y * 0.7 + y * 0.3;
  }

  function screenAngle() {
    if (screen.orientation && typeof screen.orientation.angle === "number") return screen.orientation.angle;
    const orientation = window.orientation || 0;
    return orientation < 0 ? orientation + 360 : orientation;
  }

  function clamp(value, min, max) {
    return Math.min(max, Math.max(min, value));
  }

  document.addEventListener("keydown", function (event) {
    if (event.key === "Escape") back();
  });

  render();
})();
