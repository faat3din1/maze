(function (root) {
  const core = root.MazeCore;

  function fitBoard(viewWidth, viewHeight, columns, rows, padding) {
    const pad = padding == null ? 24 : padding;
    const availW = Math.max(1, viewWidth - pad * 2);
    const availH = Math.max(1, viewHeight - pad * 2);
    const cell = Math.min(availW / columns, availH / rows);
    const boardW = cell * columns;
    const boardH = cell * rows;
    const left = (viewWidth - boardW) / 2;
    const top = (viewHeight - boardH) / 2;
    return { left: left, top: top, width: boardW, height: boardH };
  }

  function floorColor(look, type, c, r) {
    if (look === "editor") {
      if (type === core.ENTRANCE) return "#9CCC65";
      if (type === core.EXIT) return "#FFD54F";
      if (type === core.RIVER) return "#4FC3F7";
      return "#F4F1EA";
    }
    const checker = (c + r) % 2 === 0;
    let base = checker ? "#D7CBB8" : "#CBBFAE";
    if (look === "sketch") base = checker ? "#F7F3E8" : "#F3EEE3";
    if (look === "pixel") base = checker ? "#3E6B4F" : "#4E7B5A";
    if (type === core.ENTRANCE) return look === "pixel" ? "#6AB04C" : "#C5D6B0";
    if (type === core.EXIT) return look === "pixel" ? "#F9CA24" : "#E6C07B";
    if (type === core.RIVER) return look === "pixel" ? "#22A6B3" : "#7EB6C9";
    return base;
  }

  function wallColor(look) {
    if (look === "sketch") return "#222222";
    if (look === "pixel") return "#161616";
    if (look === "editor") return "#4E342E";
    return "#3E342C";
  }

  function stroke(ctx, color, x1, y1, x2, y2, width, cap) {
    ctx.beginPath();
    ctx.strokeStyle = color;
    ctx.lineWidth = width;
    ctx.lineCap = cap;
    ctx.moveTo(x1, y1);
    ctx.lineTo(x2, y2);
    ctx.stroke();
  }

  function drawMark(ctx, text, x, y, size) {
    ctx.fillStyle = "#2B241C";
    ctx.font = size + "px sans-serif";
    ctx.textAlign = "center";
    ctx.textBaseline = "alphabetic";
    ctx.fillText(text, x, y + size * 0.35);
  }

  function drawPoop(ctx, x, y, radius) {
    function circle(color, cx, cy, r) {
      ctx.beginPath();
      ctx.fillStyle = color;
      ctx.arc(cx, cy, r, 0, Math.PI * 2);
      ctx.fill();
    }
    circle("rgba(0,0,0,0.33)", x + radius * 0.12, y + radius * 0.28, radius * 0.9);
    circle("#6B3F22", x, y + radius * 0.28, radius * 0.72);
    circle("#6B3F22", x - radius * 0.28, y + radius * 0.02, radius * 0.58);
    circle("#6B3F22", x + radius * 0.26, y - radius * 0.08, radius * 0.5);
    circle("#6B3F22", x + radius * 0.02, y - radius * 0.48, radius * 0.28);
    circle("#4A2A14", x + radius * 0.02, y - radius * 0.62, radius * 0.1);
    const leftX = x - radius * 0.18;
    const leftY = y + radius * 0.12;
    const rightX = x + radius * 0.2;
    const rightY = y + radius * 0.06;
    circle("#FFFFFF", leftX, leftY, radius * 0.16);
    circle("#FFFFFF", rightX, rightY, radius * 0.16);
    circle("#2A2118", leftX + radius * 0.03, leftY, radius * 0.08);
    circle("#2A2118", rightX + radius * 0.03, rightY, radius * 0.08);
  }

  function drawWall(ctx, x1, y1, x2, y2, blocked, look, wallWidth, openWidth, seed) {
    if (!blocked && look !== "editor") return;
    const color = blocked ? wallColor(look) : "#D9D3C7";
    const width = blocked ? wallWidth : openWidth;
    const cap = look === "pixel" ? "square" : "round";
    if (look === "sketch" && blocked) {
      const jx = ((seed & 7) - 3) * 0.6;
      const jy = (((seed >> 3) & 7) - 3) * 0.6;
      stroke(ctx, color, x1 + jx, y1 + jy, x2 - jx, y2 - jy, width, cap);
      stroke(ctx, "rgba(34,34,34,0.45)", x1 - jy, y1 + jx, x2 + jy * 0.5, y2 - jx, width * 0.55, cap);
    } else {
      stroke(ctx, color, x1, y1, x2, y2, width, cap);
    }
  }

  function drawSolution(ctx, maze, dest, path, fromX, fromY) {
    const cellW = dest.width / maze.width;
    const cellH = dest.height / maze.height;
    const ahead = fromX != null && fromY != null ? path.slice(1) : path;
    const points = [];
    if (fromX != null && fromY != null) points.push([dest.left + fromX * cellW, dest.top + fromY * cellH]);
    ahead.forEach(function (index) {
      points.push([
        dest.left + ((index % maze.width) + 0.5) * cellW,
        dest.top + (Math.floor(index / maze.width) + 0.5) * cellH,
      ]);
    });
    if (points.length < 2) return;
    const width = Math.min(cellW, cellH) * 0.1;
    ctx.lineCap = "round";
    ctx.strokeStyle = "rgba(255,246,208,0.6)";
    ctx.lineWidth = width;
    ctx.beginPath();
    ctx.moveTo(points[0][0], points[0][1]);
    for (let i = 1; i < points.length; i += 1) ctx.lineTo(points[i][0], points[i][1]);
    ctx.stroke();
  }

  function horizontalBlocked(maze, c, r, look) {
    if (look === "editor") {
      if (r === 0) return (maze.cells[c].walls & core.UP.mask) !== 0;
      if (r === maze.height) return (maze.cells[(maze.height - 1) * maze.width + c].walls & core.DOWN.mask) !== 0;
      return core.editorBlocked(maze, c, r, core.UP);
    }
    return r === 0 || r === maze.height || core.playBlocked(maze, c, r, core.UP);
  }

  function verticalBlocked(maze, c, r, look) {
    if (look === "editor") {
      if (c === 0) return (maze.cells[r * maze.width].walls & core.LEFT.mask) !== 0;
      if (c === maze.width) return (maze.cells[r * maze.width + maze.width - 1].walls & core.RIGHT.mask) !== 0;
      return core.editorBlocked(maze, c, r, core.LEFT);
    }
    return c === 0 || c === maze.width || core.playBlocked(maze, c, r, core.LEFT);
  }

  function drawMazeScene(ctx, maze, look, dest, ballX, ballY, ballRadiusCells, solution) {
    const cellW = dest.width / maze.width;
    const cellH = dest.height / maze.height;
    const markSize = Math.min(cellW, cellH) * 0.38;
    for (let r = 0; r < maze.height; r += 1) {
      for (let c = 0; c < maze.width; c += 1) {
        const item = maze.cells[r * maze.width + c];
        ctx.fillStyle = floorColor(look, item.type, c, r);
        ctx.fillRect(dest.left + c * cellW, dest.top + r * cellH, cellW, cellH);
        if (look === "editor") {
          const mark = item.type === core.ENTRANCE ? "入" : item.type === core.EXIT ? "出" : item.type === core.RIVER ? "河" : null;
          if (mark) drawMark(ctx, mark, dest.left + (c + 0.5) * cellW, dest.top + (r + 0.5) * cellH, markSize);
        }
      }
    }
    const wallWidth = Math.min(cellW, cellH) * (look === "pixel" ? 0.16 : 0.1);
    const openWidth = Math.min(cellW, cellH) * 0.035;
    for (let r = 0; r <= maze.height; r += 1) {
      for (let c = 0; c < maze.width; c += 1) {
        drawWall(
          ctx,
          dest.left + c * cellW,
          dest.top + r * cellH,
          dest.left + (c + 1) * cellW,
          dest.top + r * cellH,
          horizontalBlocked(maze, c, r, look),
          look,
          wallWidth,
          openWidth,
          c * 17 + r
        );
      }
    }
    for (let c = 0; c <= maze.width; c += 1) {
      for (let r = 0; r < maze.height; r += 1) {
        drawWall(
          ctx,
          dest.left + c * cellW,
          dest.top + r * cellH,
          dest.left + c * cellW,
          dest.top + (r + 1) * cellH,
          verticalBlocked(maze, c, r, look),
          look,
          wallWidth,
          openWidth,
          c * 31 + r * 3
        );
      }
    }
    if (solution) drawSolution(ctx, maze, dest, solution, ballX, ballY);
    if (look !== "editor") {
      const exit = core.findType(maze, core.EXIT);
      if (exit != null) {
        drawMark(
          ctx,
          "出",
          dest.left + ((exit % maze.width) + 0.5) * cellW,
          dest.top + (Math.floor(exit / maze.width) + 0.5) * cellH,
          markSize
        );
      }
    }
    if (ballX != null && ballY != null) {
      drawPoop(ctx, dest.left + ballX * cellW, dest.top + ballY * cellH, ballRadiusCells * Math.min(cellW, cellH));
    }
  }

  root.MazeDraw = { fitBoard: fitBoard, drawMazeScene: drawMazeScene };
})(typeof globalThis !== "undefined" ? globalThis : this);
