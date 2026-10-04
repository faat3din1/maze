(function (root, factory) {
  const core = root.MazeCore || require("./maze.js");
  const api = factory(core);
  if (typeof module === "object" && module.exports) module.exports = api;
  root.MazePhysics = api;
})(typeof globalThis !== "undefined" ? globalThis : this, function (core) {
  function hypot(x, y) {
    return Math.hypot(x, y);
  }

  function clamp(value, min, max) {
    return Math.min(max, Math.max(min, value));
  }

  function wallSegments(maze) {
    const list = [];
    for (let r = 0; r <= maze.height; r += 1) {
      for (let c = 0; c < maze.width; c += 1) {
        const blocked = r === 0 || r === maze.height || core.playBlocked(maze, c, r, core.UP);
        if (blocked) list.push({ x1: c, y1: r, x2: c + 1, y2: r });
      }
    }
    for (let c = 0; c <= maze.width; c += 1) {
      for (let r = 0; r < maze.height; r += 1) {
        const blocked = c === 0 || c === maze.width || core.playBlocked(maze, c, r, core.LEFT);
        if (blocked) list.push({ x1: c, y1: r, x2: c, y2: r + 1 });
      }
    }
    return list;
  }

  function closest(segment, px, py) {
    const dx = segment.x2 - segment.x1;
    const dy = segment.y2 - segment.y1;
    const len2 = dx * dx + dy * dy;
    const t = len2 === 0 ? 0 : clamp(((px - segment.x1) * dx + (py - segment.y1) * dy) / len2, 0, 1);
    return [segment.x1 + t * dx, segment.y1 + t * dy];
  }

  function MarbleSim(maze) {
    this.maze = maze;
    this.radius = 0.2;
    this.segments = wallSegments(maze);
    this.entrance = core.findType(maze, core.ENTRANCE);
    if (this.entrance == null) this.entrance = 0;
    this.ax = 0;
    this.ay = 0;
    this.won = false;
    this.riverFlash = 0;
    this.accumulator = 0;
    this.reset();
  }

  MarbleSim.prototype.reset = function () {
    this.x = (this.entrance % this.maze.width) + 0.5;
    this.y = Math.floor(this.entrance / this.maze.width) + 0.5;
    this.vx = 0;
    this.vy = 0;
    this.won = false;
    this.accumulator = 0;
  };

  MarbleSim.prototype.step = function (dt) {
    if (this.won) return;
    this.accumulator += clamp(dt, 0, 0.05);
    const fixed = 1 / 120;
    let guard = 0;
    while (this.accumulator >= fixed && guard < 10 && !this.won) {
      this.integrate(fixed);
      this.accumulator -= fixed;
      guard += 1;
    }
  };

  MarbleSim.prototype.integrate = function (dt) {
    this.vx += this.ax * dt;
    this.vy += this.ay * dt;
    const damp = Math.exp(-2.4 * dt);
    this.vx *= damp;
    this.vy *= damp;
    const speed = hypot(this.vx, this.vy);
    const maxSpeed = 5.5;
    if (speed > maxSpeed) {
      this.vx *= maxSpeed / speed;
      this.vy *= maxSpeed / speed;
    }
    this.x += this.vx * dt;
    this.y += this.vy * dt;
    for (let pass = 0; pass < 6; pass += 1) {
      if (!this.separate()) break;
    }
    this.x = clamp(this.x, this.radius, this.maze.width - this.radius);
    this.y = clamp(this.y, this.radius, this.maze.height - this.radius);
    const c = clamp(Math.floor(this.x), 0, this.maze.width - 1);
    const r = clamp(Math.floor(this.y), 0, this.maze.height - 1);
    const type = this.maze.cells[r * this.maze.width + c].type;
    if (type === core.EXIT) {
      this.vx = 0;
      this.vy = 0;
      this.won = true;
    } else if (type === core.RIVER) {
      this.riverFlash += 1;
      this.x = (this.entrance % this.maze.width) + 0.5;
      this.y = Math.floor(this.entrance / this.maze.width) + 0.5;
      this.vx = 0;
      this.vy = 0;
    }
  };

  MarbleSim.prototype.separate = function () {
    let hit = false;
    for (let i = 0; i < this.segments.length; i += 1) {
      const segment = this.segments[i];
      const point = closest(segment, this.x, this.y);
      let dx = this.x - point[0];
      let dy = this.y - point[1];
      let dist = hypot(dx, dy);
      if (dist < 1e-4) {
        const nx = -(segment.y2 - segment.y1);
        const ny = segment.x2 - segment.x1;
        const length = Math.max(1e-4, hypot(nx, ny));
        dx = nx / length;
        dy = ny / length;
        dist = 0;
      } else {
        dx /= dist;
        dy /= dist;
      }
      if (dist >= this.radius) continue;
      const push = this.radius - dist + 0.0015;
      this.x += dx * push;
      this.y += dy * push;
      const vn = this.vx * dx + this.vy * dy;
      if (vn < 0) {
        const restitution = vn < -0.7 ? 0.55 : 0;
        this.vx -= (1 + restitution) * vn * dx;
        this.vy -= (1 + restitution) * vn * dy;
        if (restitution > 0) {
          const tx = -dy;
          const ty = dx;
          const vt = this.vx * tx + this.vy * ty;
          this.vx -= vt * 0.35 * tx;
          this.vy -= vt * 0.35 * ty;
        }
      }
      hit = true;
    }
    return hit;
  };

  return { MarbleSim: MarbleSim };
});
