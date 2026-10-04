(function (root, factory) {
  const api = factory();
  if (typeof module === "object" && module.exports) module.exports = api;
  root.MazeCore = api;
})(typeof globalThis !== "undefined" ? globalThis : this, function () {
  const ROAD = 0;
  const ENTRANCE = 1;
  const EXIT = 2;
  const RIVER = 4;
  const MIN_WIDTH = 4;
  const MAX_WIDTH = 20;
  const MIN_HEIGHT = 4;
  const MAX_HEIGHT = 40;

  const UP = { dx: 0, dy: -1, mask: 0b1000 };
  const RIGHT = { dx: 1, dy: 0, mask: 0b0100 };
  const DOWN = { dx: 0, dy: 1, mask: 0b0010 };
  const LEFT = { dx: -1, dy: 0, mask: 0b0001 };
  const DIRS = [UP, RIGHT, DOWN, LEFT];

  function opposite(dir) {
    if (dir === UP) return DOWN;
    if (dir === RIGHT) return LEFT;
    if (dir === DOWN) return UP;
    return RIGHT;
  }

  function clamp(value, min, max) {
    return Math.min(max, Math.max(min, value));
  }

  function cell(type, walls) {
    return { type: type, walls: walls & 0b1111 };
  }

  function withWall(source, dir, blocked) {
    const walls = blocked ? source.walls | dir.mask : source.walls & ~dir.mask;
    return cell(source.type, walls);
  }

  function encodeByte(source) {
    return (source.type << 5) | (source.walls & 0b1111);
  }

  function decodeByte(byte) {
    if ((byte & 0b00010000) !== 0) return null;
    const type = (byte >>> 5) & 0b111;
    if (type !== ROAD && type !== ENTRANCE && type !== EXIT && type !== RIVER) return null;
    return cell(type, byte);
  }

  function maze(width, height, cells) {
    return { width: width, height: height, cells: cells };
  }

  function indexOf(grid, c, r) {
    return r * grid.width + c;
  }

  function cellAt(grid, c, r) {
    return grid.cells[indexOf(grid, c, r)];
  }

  function inBounds(grid, c, r) {
    return c >= 0 && r >= 0 && c < grid.width && r < grid.height;
  }

  function blockedBit(source, dir) {
    return (source.walls & dir.mask) !== 0;
  }

  function editorBlocked(grid, c, r, dir) {
    const own = blockedBit(cellAt(grid, c, r), dir);
    const nc = c + dir.dx;
    const nr = r + dir.dy;
    if (!inBounds(grid, nc, nr)) return own;
    return own || blockedBit(cellAt(grid, nc, nr), opposite(dir));
  }

  function playBlocked(grid, c, r, dir) {
    const nc = c + dir.dx;
    const nr = r + dir.dy;
    if (!inBounds(grid, nc, nr)) return true;
    return blockedBit(cellAt(grid, c, r), dir) || blockedBit(cellAt(grid, nc, nr), opposite(dir));
  }

  function findType(grid, type) {
    const index = grid.cells.findIndex(function (item) { return item.type === type; });
    return index >= 0 ? index : null;
  }

  function withCell(grid, index, nextCell) {
    const cells = grid.cells.slice();
    cells[index] = nextCell;
    return maze(grid.width, grid.height, cells);
  }

  function paintType(grid, index, type) {
    const cleared = type === ENTRANCE || type === EXIT
      ? grid.cells.map(function (item) {
        return item.type === type ? cell(ROAD, item.walls) : item;
      })
      : grid.cells;
    const cells = cleared.slice();
    cells[index] = cell(type, cells[index].walls);
    return maze(grid.width, grid.height, cells);
  }

  function setEdge(grid, c, r, dir, blocked) {
    const cells = grid.cells.slice();
    const i = indexOf(grid, c, r);
    cells[i] = withWall(cells[i], dir, blocked);
    const nc = c + dir.dx;
    const nr = r + dir.dy;
    if (inBounds(grid, nc, nr)) {
      const j = indexOf(grid, nc, nr);
      cells[j] = withWall(cells[j], opposite(dir), blocked);
    }
    return maze(grid.width, grid.height, cells);
  }

  function toggleEdge(grid, c, r, dir) {
    return setEdge(grid, c, r, dir, !editorBlocked(grid, c, r, dir));
  }

  function sealBorder(grid) {
    let next = grid;
    for (let c = 0; c < grid.width; c += 1) {
      next = setEdge(next, c, 0, UP, true);
      next = setEdge(next, c, grid.height - 1, DOWN, true);
    }
    for (let r = 0; r < grid.height; r += 1) {
      next = setEdge(next, 0, r, LEFT, true);
      next = setEdge(next, grid.width - 1, r, RIGHT, true);
    }
    return next;
  }

  function blank(width, height) {
    const cells = [];
    for (let i = 0; i < width * height; i += 1) cells.push(cell(ROAD, 0));
    return maze(width, height, cells);
  }

  function sameMaze(a, b) {
    if (!a || !b || a.width !== b.width || a.height !== b.height || a.cells.length !== b.cells.length) return false;
    for (let i = 0; i < a.cells.length; i += 1) {
      if (a.cells[i].type !== b.cells[i].type || a.cells[i].walls !== b.cells[i].walls) return false;
    }
    return true;
  }

  function hasOpenSide(grid, index) {
    const c = index % grid.width;
    const r = Math.floor(index / grid.width);
    return DIRS.some(function (dir) {
      return inBounds(grid, c + dir.dx, r + dir.dy) && !playBlocked(grid, c, r, dir);
    });
  }

  function borderIsClosed(grid) {
    for (let c = 0; c < grid.width; c += 1) {
      if (!blockedBit(cellAt(grid, c, 0), UP)) return false;
      if (!blockedBit(cellAt(grid, c, grid.height - 1), DOWN)) return false;
    }
    for (let r = 0; r < grid.height; r += 1) {
      if (!blockedBit(cellAt(grid, 0, r), LEFT)) return false;
      if (!blockedBit(cellAt(grid, grid.width - 1, r), RIGHT)) return false;
    }
    return true;
  }

  function openNeighbors(grid, index) {
    const c = index % grid.width;
    const r = Math.floor(index / grid.width);
    const neighbors = [];
    DIRS.forEach(function (dir) {
      const nc = c + dir.dx;
      const nr = r + dir.dy;
      if (!inBounds(grid, nc, nr) || playBlocked(grid, c, r, dir)) return;
      neighbors.push(indexOf(grid, nc, nr));
    });
    return neighbors;
  }

  function solutionPath(grid, start) {
    const origin = start >= 0 ? start : findType(grid, ENTRANCE);
    if (origin == null || origin < 0 || origin >= grid.cells.length) return null;
    const goal = findType(grid, EXIT);
    if (goal == null) return null;
    if (origin === goal) return [goal];
    const prev = new Array(grid.cells.length).fill(-1);
    const queue = [origin];
    prev[origin] = origin;
    for (let head = 0; head < queue.length; head += 1) {
      const current = queue[head];
      if (current === goal) break;
      openNeighbors(grid, current).forEach(function (next) {
        if (prev[next] >= 0 || grid.cells[next].type === RIVER) return;
        prev[next] = current;
        queue.push(next);
      });
    }
    if (prev[goal] < 0) return null;
    const path = [];
    let cursor = goal;
    while (cursor !== origin) {
      path.push(cursor);
      cursor = prev[cursor];
    }
    path.push(origin);
    path.reverse();
    return path;
  }

  function reachesExit(grid, start, goal) {
    const seen = new Array(grid.cells.length).fill(false);
    const queue = [start];
    seen[start] = true;
    for (let head = 0; head < queue.length; head += 1) {
      const current = queue[head];
      if (current === goal) return true;
      openNeighbors(grid, current).forEach(function (next) {
        if (seen[next] || grid.cells[next].type === RIVER) return;
        seen[next] = true;
        queue.push(next);
      });
    }
    return false;
  }

  function validate(grid) {
    const errors = [];
    if (grid.width < MIN_WIDTH || grid.width > MAX_WIDTH) errors.push("寬度必須在 " + MIN_WIDTH + " 到 " + MAX_WIDTH + " 之間");
    if (grid.height < MIN_HEIGHT || grid.height > MAX_HEIGHT) errors.push("高度必須在 " + MIN_HEIGHT + " 到 " + MAX_HEIGHT + " 之間");
    const entrances = grid.cells.filter(function (item) { return item.type === ENTRANCE; }).length;
    const exits = grid.cells.filter(function (item) { return item.type === EXIT; }).length;
    if (entrances !== 1) errors.push("必須恰好有一個入口");
    if (exits !== 1) errors.push("必須恰好有一個出口");
    if (!borderIsClosed(grid)) errors.push("外框未封閉");
    const entrance = findType(grid, ENTRANCE);
    const exit = findType(grid, EXIT);
    if (entrance != null && !hasOpenSide(grid, entrance)) errors.push("入口被封死");
    if (exit != null && !hasOpenSide(grid, exit)) errors.push("出口被封死");
    if (entrance != null && exit != null && !reachesExit(grid, entrance, exit)) errors.push("入口走不到出口");
    return errors;
  }

  function randInt(n) {
    return Math.floor(Math.random() * n);
  }

  function shuffle(list) {
    for (let i = list.length - 1; i > 0; i -= 1) {
      const j = randInt(i + 1);
      const swap = list[i];
      list[i] = list[j];
      list[j] = swap;
    }
    return list;
  }

  function createGrid(width, height) {
    function filled() {
      return Array.from({ length: height }, function () { return Array(width).fill(true); });
    }
    return { width: width, height: height, up: filled(), right: filled(), down: filled(), left: filled() };
  }

  function openWall(grid, c, r, dir) {
    const nc = c + dir.dx;
    const nr = r + dir.dy;
    if (dir === UP) {
      grid.up[r][c] = false;
      grid.down[nr][nc] = false;
    } else if (dir === RIGHT) {
      grid.right[r][c] = false;
      grid.left[nr][nc] = false;
    } else if (dir === DOWN) {
      grid.down[r][c] = false;
      grid.up[nr][nc] = false;
    } else {
      grid.left[r][c] = false;
      grid.right[nr][nc] = false;
    }
  }

  function carveDfs(grid) {
    const seen = Array.from({ length: grid.height }, function () { return Array(grid.width).fill(false); });
    const stack = [[0, 0]];
    seen[0][0] = true;
    while (stack.length) {
      const top = stack[stack.length - 1];
      const c = top[0];
      const r = top[1];
      const next = DIRS.filter(function (dir) {
        const nc = c + dir.dx;
        const nr = r + dir.dy;
        return nc >= 0 && nr >= 0 && nc < grid.width && nr < grid.height && !seen[nr][nc];
      });
      if (!next.length) stack.pop();
      else {
        const dir = next[randInt(next.length)];
        const nc = c + dir.dx;
        const nr = r + dir.dy;
        openWall(grid, c, r, dir);
        seen[nr][nc] = true;
        stack.push([nc, nr]);
      }
    }
  }

  function carveKruskal(grid) {
    const parents = Array.from({ length: grid.width * grid.height }, function (_, i) { return i; });
    function find(i) {
      let x = i;
      while (parents[x] !== x) {
        parents[x] = parents[parents[x]];
        x = parents[x];
      }
      return x;
    }
    const edges = [];
    for (let r = 0; r < grid.height; r += 1) {
      for (let c = 0; c < grid.width; c += 1) {
        if (c + 1 < grid.width) edges.push([c, r, RIGHT]);
        if (r + 1 < grid.height) edges.push([c, r, DOWN]);
      }
    }
    shuffle(edges);
    edges.forEach(function (edge) {
      const c = edge[0];
      const r = edge[1];
      const dir = edge[2];
      const a = r * grid.width + c;
      const b = (r + dir.dy) * grid.width + (c + dir.dx);
      const pa = find(a);
      const pb = find(b);
      if (pa !== pb) {
        parents[pa] = pb;
        openWall(grid, c, r, dir);
      }
    });
  }

  function carvePrim(grid) {
    const inside = Array.from({ length: grid.height }, function () { return Array(grid.width).fill(false); });
    const frontier = [];
    function grow(c, r) {
      inside[r][c] = true;
      DIRS.forEach(function (dir) {
        const nc = c + dir.dx;
        const nr = r + dir.dy;
        if (nc >= 0 && nr >= 0 && nc < grid.width && nr < grid.height && !inside[nr][nc]) frontier.push([c, r, dir]);
      });
    }
    grow(0, 0);
    while (frontier.length) {
      const pick = frontier.splice(randInt(frontier.length), 1)[0];
      const c = pick[0];
      const r = pick[1];
      const dir = pick[2];
      const nc = c + dir.dx;
      const nr = r + dir.dy;
      if (nc < 0 || nr < 0 || nc >= grid.width || nr >= grid.height || inside[nr][nc]) continue;
      openWall(grid, c, r, dir);
      grow(nc, nr);
    }
  }

  function openLoops(grid, fraction) {
    const closed = [];
    for (let r = 0; r < grid.height; r += 1) {
      for (let c = 0; c < grid.width; c += 1) {
        if (c + 1 < grid.width && grid.right[r][c]) closed.push([c, r, RIGHT]);
        if (r + 1 < grid.height && grid.down[r][c]) closed.push([c, r, DOWN]);
      }
    }
    shuffle(closed);
    const count = Math.floor(closed.length * fraction);
    for (let i = 0; i < count; i += 1) openWall(grid, closed[i][0], closed[i][1], closed[i][2]);
  }

  function gridToMaze(grid) {
    const cells = [];
    for (let index = 0; index < grid.width * grid.height; index += 1) {
      const c = index % grid.width;
      const r = Math.floor(index / grid.width);
      let walls = 0;
      if (grid.up[r][c]) walls |= UP.mask;
      if (grid.right[r][c]) walls |= RIGHT.mask;
      if (grid.down[r][c]) walls |= DOWN.mask;
      if (grid.left[r][c]) walls |= LEFT.mask;
      cells.push(cell(ROAD, walls));
    }
    return maze(grid.width, grid.height, cells);
  }

  function farthestCell(grid, start) {
    const dist = new Array(grid.cells.length).fill(-1);
    const queue = [start];
    dist[start] = 0;
    for (let head = 0; head < queue.length; head += 1) {
      const current = queue[head];
      openNeighbors(grid, current).forEach(function (next) {
        if (dist[next] >= 0) return;
        dist[next] = dist[current] + 1;
        queue.push(next);
      });
    }
    let best = start;
    for (let i = 0; i < dist.length; i += 1) if (dist[i] > dist[best]) best = i;
    return best;
  }

  function withOpenRiver(grid, index) {
    const c = index % grid.width;
    const r = Math.floor(index / grid.width);
    let next = grid;
    let walls = grid.cells[index].walls;
    DIRS.forEach(function (dir) {
      const nc = c + dir.dx;
      const nr = r + dir.dy;
      if (!inBounds(grid, nc, nr)) return;
      walls &= ~dir.mask;
      const neighbor = indexOf(grid, nc, nr);
      next = withCell(next, neighbor, withWall(next.cells[neighbor], opposite(dir), false));
    });
    return withCell(next, index, cell(RIVER, walls));
  }

  function placeRivers(grid, rivers) {
    if (rivers <= 0) return grid;
    const path = solutionPath(grid);
    if (!path) return grid;
    const onPath = new Set(path);
    const spots = [];
    for (let i = 0; i < grid.cells.length; i += 1) if (!onPath.has(i)) spots.push(i);
    if (!spots.length) return grid;
    shuffle(spots);
    let result = grid;
    spots.slice(0, Math.min(rivers, spots.length)).forEach(function (index) {
      result = withOpenRiver(result, index);
    });
    return result;
  }

  function generateMaze(algorithm, width, height, rivers, loopFraction) {
    const grid = createGrid(width, height);
    if (algorithm === "kruskal") carveKruskal(grid);
    else if (algorithm === "prim") carvePrim(grid);
    else carveDfs(grid);
    if (loopFraction > 0) openLoops(grid, loopFraction);
    let result = gridToMaze(grid);
    const exit = farthestCell(result, 0);
    result = withCell(result, 0, cell(ENTRANCE, result.cells[0].walls));
    result = withCell(result, exit, cell(EXIT, result.cells[exit].walls));
    return placeRivers(result, rivers);
  }

  function crc16(data) {
    let crc = 0xffff;
    for (let i = 0; i < data.length; i += 1) {
      crc ^= (data[i] & 0xff) << 8;
      for (let bit = 0; bit < 8; bit += 1) {
        crc = (crc & 0x8000) !== 0 ? (crc << 1) ^ 0x1021 : crc << 1;
        crc &= 0xffff;
      }
    }
    return crc;
  }

  function encode(grid) {
    const count = grid.width * grid.height;
    const body = new Uint8Array(5 + count);
    body[0] = 77;
    body[1] = 90;
    body[2] = 1;
    body[3] = grid.width;
    body[4] = grid.height;
    for (let i = 0; i < count; i += 1) body[5 + i] = encodeByte(grid.cells[i]);
    const crc = crc16(body);
    const out = new Uint8Array(body.length + 2);
    out.set(body);
    out[body.length] = (crc >> 8) & 0xff;
    out[body.length + 1] = crc & 0xff;
    return out;
  }

  function fail(reasons) {
    return { ok: false, reasons: reasons };
  }

  function decode(bytes) {
    const data = bytes instanceof Uint8Array ? bytes : Uint8Array.from(bytes);
    if (data.length < 7) return fail(["QR 內容太短"]);
    const body = data.subarray(0, data.length - 2);
    const expected = crc16(body);
    const actual = ((data[data.length - 2] & 0xff) << 8) | (data[data.length - 1] & 0xff);
    if (expected !== actual) return fail(["校驗失敗"]);
    if (body[0] !== 77 || body[1] !== 90) return fail(["不是迷宮 QR"]);
    if (body[2] !== 1) return fail(["版本不支援"]);
    const width = body[3] & 0xff;
    const height = body[4] & 0xff;
    if (width < MIN_WIDTH || width > MAX_WIDTH || height < MIN_HEIGHT || height > MAX_HEIGHT) return fail(["地圖尺寸不合法"]);
    if (body.length !== 5 + width * height) return fail(["地圖長度不符"]);
    const cells = [];
    for (let i = 0; i < width * height; i += 1) {
      const decoded = decodeByte(body[5 + i] & 0xff);
      if (!decoded) return fail(["第 " + (i % width) + "," + Math.floor(i / width) + " 格類型不合法"]);
      cells.push(decoded);
    }
    const grid = maze(width, height, cells);
    const errors = validate(grid);
    return errors.length ? fail(errors) : { ok: true, maze: grid };
  }

  function bytesToLatin1(bytes) {
    let text = "";
    for (let i = 0; i < bytes.length; i += 1) text += String.fromCharCode(bytes[i] & 0xff);
    return text;
  }

  function latin1ToBytes(text) {
    const out = new Uint8Array(text.length);
    for (let i = 0; i < text.length; i += 1) out[i] = text.charCodeAt(i) & 0xff;
    return out;
  }

  function bytesToBase64(bytes) {
    return btoa(bytesToLatin1(bytes));
  }

  function normalizeShareText(raw) {
    return String(raw || "").replace(/\s/g, "");
  }

  function decodeShareText(raw) {
    const compact = normalizeShareText(raw);
    if (!compact) return fail(["沒有文字"]);
    try {
      return decode(latin1ToBytes(atob(compact)));
    } catch (err) {
      return fail(["不是迷宮文字"]);
    }
  }

  function decodeQrText(text) {
    return decode(latin1ToBytes(text));
  }

  function editAt(grid, tool, gx, gy) {
    const c = Math.floor(gx);
    const r = Math.floor(gy);
    if (!inBounds(grid, c, r)) return null;
    let next;
    if (tool === "wall") {
      const fx = gx - c;
      const fy = gy - r;
      const options = [[UP, fy], [DOWN, 1 - fy], [LEFT, fx], [RIGHT, 1 - fx]];
      options.sort(function (a, b) { return a[1] - b[1]; });
      if (options[0][1] > 0.28) return null;
      next = toggleEdge(grid, c, r, options[0][0]);
    } else {
      const type = tool === "entrance" ? ENTRANCE : tool === "exit" ? EXIT : tool === "river" ? RIVER : ROAD;
      next = paintType(grid, indexOf(grid, c, r), type);
    }
    return sameMaze(next, grid) ? null : next;
  }

  function levelSpec(difficulty, width, height, rivers) {
    if (difficulty === "easy") return { width: 8, height: 6, rivers: 0, loops: 0.12 };
    if (difficulty === "normal") return { width: 12, height: 10, rivers: 4, loops: 0 };
    if (difficulty === "hard") return { width: 16, height: 16, rivers: 10, loops: 0 };
    return {
      width: clamp(width, MIN_WIDTH, MAX_WIDTH),
      height: clamp(height, MIN_HEIGHT, MAX_HEIGHT),
      rivers: clamp(rivers, 0, 40),
      loops: 0,
    };
  }

  function preset(algorithm, difficulty) {
    const level = levelSpec(difficulty, 0, 0, 0);
    return {
      algorithm: algorithm,
      difficulty: difficulty,
      width: level.width,
      height: level.height,
      rivers: level.rivers,
      loops: level.loops,
    };
  }

  function upgrade(spec) {
    if (spec.difficulty === "easy") return preset(spec.algorithm, "normal");
    if (spec.difficulty === "normal") return preset(spec.algorithm, "hard");
    return {
      algorithm: spec.algorithm,
      difficulty: "custom",
      width: Math.min(MAX_WIDTH, spec.width + 2),
      height: Math.min(MAX_HEIGHT, spec.height + 2),
      rivers: Math.min(40, spec.rivers + 2),
      loops: 0,
    };
  }

  function levelBlurb(difficulty, width, height, rivers) {
    const spec = levelSpec(difficulty, width, height, rivers);
    return spec.width + "×" + spec.height + "，河流 " + spec.rivers + (spec.loops > 0 ? "，有環路" : "，完美迷宮");
  }

  return {
    ROAD: ROAD,
    ENTRANCE: ENTRANCE,
    EXIT: EXIT,
    RIVER: RIVER,
    UP: UP,
    RIGHT: RIGHT,
    DOWN: DOWN,
    LEFT: LEFT,
    DIRS: DIRS,
    MIN_WIDTH: MIN_WIDTH,
    MAX_WIDTH: MAX_WIDTH,
    MIN_HEIGHT: MIN_HEIGHT,
    MAX_HEIGHT: MAX_HEIGHT,
    blank: blank,
    sealBorder: sealBorder,
    playBlocked: playBlocked,
    editorBlocked: editorBlocked,
    findType: findType,
    solutionPath: solutionPath,
    validate: validate,
    generateMaze: generateMaze,
    encode: encode,
    decode: decode,
    decodeQrText: decodeQrText,
    decodeShareText: decodeShareText,
    bytesToBase64: bytesToBase64,
    bytesToLatin1: bytesToLatin1,
    editAt: editAt,
    levelSpec: levelSpec,
    upgrade: upgrade,
    levelBlurb: levelBlurb,
    fail: fail,
    sameMaze: sameMaze,
  };
});
