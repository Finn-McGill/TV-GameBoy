package tvgameboy.games.btd5;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

import tvgameboy.shared.Game;

public final class BTD5Game implements Game {
    private List<Tower> towers = new ArrayList<>();
    private List<Balloon> balloons = new ArrayList<>();
    private int money = 5000;
    private int lives = 20;
    private int round = 1;
    private boolean roundStarted = false;
    private GamePath gamePath;
    // UI label to show money/lives/round
    private javax.swing.JLabel infoLabel;
    // number of active projectiles/effects (updated by canvas)
    private volatile int activeProjectiles = 0;
    // Selected tower type for placement
    private TowerType selectedTowerType = TowerType.NORMAL;

    private enum TowerType {
        NORMAL,
        NINJA
    }

    // Costs for tower types
    private static final int COST_NORMAL = 1000;
    private static final int COST_NINJA = 1500;

    private static int getCostFor(TowerType t) {
        return t == TowerType.NINJA ? COST_NINJA : COST_NORMAL;
    }

    private static String getDescriptionFor(TowerType t) {
        return t == TowerType.NINJA ? "Ninja: throws shurikens at nearby balloons (range ~100)" : "Normal: basic monkey that pops balloons up close";
    }

    // Visual / gameplay constants
    private static final int BALLOON_RADIUS = 10; // larger balloons
    private static final int SHURIKEN_RADIUS = 4;
    private static final int PEBBLE_RADIUS = 5;
    private static final int MONEY_PER_POP = 50; // reward per popped balloon


    @Override
    public JComponent getView(Runnable returnToMenu) {
        // Create game path with 2 turns - uniform segment lengths for constant speed
        gamePath = new GamePath();
        // Start going right with uniform segments
        gamePath.addPoint(20, 200);
        gamePath.addPoint(100, 200);
        gamePath.addPoint(180, 200);
        gamePath.addPoint(260, 200);
        gamePath.addPoint(340, 200);
        gamePath.addPoint(420, 200);
        gamePath.addPoint(500, 200);
        // First turn: go down with uniform segments
        gamePath.addPoint(520, 270);
        gamePath.addPoint(520, 340);
        // Second turn: go right
        gamePath.addPoint(600, 340);
        gamePath.addPoint(680, 340);
        gamePath.addPoint(760, 340);



        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(new Color(30, 30, 30));

        // Top Bar with Info and Buttons
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBackground(new Color(30, 30, 30));
        topBar.setBorder(javax.swing.BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JButton menuButton = new JButton("Menu");
        menuButton.setFont(new Font("Segoe UI", Font.BOLD, 14));
        menuButton.setBackground(new Color(0, 150, 0));
        menuButton.setForeground(new Color(255, 255, 255));
        menuButton.setFocusPainted(false);
        menuButton.addActionListener(event -> returnToMenu.run());

        JPanel infoPanel = new JPanel();
        infoPanel.setBackground(new Color(30, 30, 30));
        this.infoLabel = new javax.swing.JLabel("Money: $" + money + " | Lives: " + lives + " | Round: " + round);
        this.infoLabel.setForeground(new Color(255, 255, 255));
        this.infoLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        infoPanel.add(this.infoLabel);

        JButton startRoundButton = new JButton("Start Round");
        startRoundButton.setFont(new Font("Segoe UI", Font.BOLD, 14));
        startRoundButton.setBackground(new Color(200, 0, 0));
        startRoundButton.setForeground(new Color(255, 255, 255));
        startRoundButton.setFocusPainted(false);
        startRoundButton.addActionListener(event -> {
            if (!roundStarted) {
                roundStarted = true;
                startRoundButton.setEnabled(false);
                this.infoLabel.setText("Money: $" + money + " | Lives: " + lives + " | Round: " + round + " (RUNNING)");
                // Spawn some balloons over time so towers can interact
                new Thread(() -> {
                    try {
                        for (int i = 0; i < 8; i++) {
                            Balloon b = new Balloon();
                            // initialize at first point if available
                            if (gamePath.getPoints().size() > 0) {
                                int[] p = gamePath.getPoints().get(0);
                                b.x = p[0];
                                b.y = p[1];
                            }
                            // avoid overlapping: wait while a balloon is too close to start
                            synchronized (balloons) {
                                while (true) {
                                    boolean tooClose = false;
                                    for (Balloon ob : balloons) {
                                        double dx = ob.x - b.x;
                                        double dy = ob.y - b.y;
                                        if (Math.hypot(dx, dy) < BALLOON_RADIUS * 4) { tooClose = true; break; }
                                    }
                                    if (!tooClose) break;
                                    Thread.sleep(150);
                                }
                                balloons.add(b);
                            }
                            Thread.sleep(500);
                        }

                        // Wait until all balloons and active projectiles/effects are finished
                        while (true) {
                            synchronized (balloons) {
                                if (balloons.isEmpty() && activeProjectiles == 0) break;
                            }
                            Thread.sleep(200);
                        }

                        roundStarted = false;
                        round++;
                        money += 500; // increased reward per round
                        startRoundButton.setEnabled(true);
                        this.infoLabel.setText("Money: $" + money + " | Lives: " + lives + " | Round: " + round);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }).start();
            }
        });

        topBar.add(menuButton, BorderLayout.WEST);
        topBar.add(infoPanel, BorderLayout.CENTER);
        topBar.add(startRoundButton, BorderLayout.EAST);

        // Game Canvas
        GameCanvas gameCanvas = new GameCanvas(this);

        // Selection panel for monkey types (smaller)
        JPanel selectPanel = new JPanel();
        selectPanel.setBackground(new Color(30, 30, 30));
        selectPanel.setBorder(javax.swing.BorderFactory.createEmptyBorder(8, 8, 8, 8));
        selectPanel.setPreferredSize(new java.awt.Dimension(120, 0));
        JButton normalButton = new JButton("Normal\n($" + COST_NORMAL + ")");
        JButton ninjaButton = new JButton("Ninja\n($" + COST_NINJA + ")");
        normalButton.setToolTipText(getDescriptionFor(TowerType.NORMAL));
        ninjaButton.setToolTipText(getDescriptionFor(TowerType.NINJA));
        normalButton.setFocusPainted(false);
        ninjaButton.setFocusPainted(false);
        normalButton.setPreferredSize(new java.awt.Dimension(100, 28));
        ninjaButton.setPreferredSize(new java.awt.Dimension(100, 28));
        normalButton.addActionListener(e -> {
            selectedTowerType = TowerType.NORMAL;
            normalButton.setBackground(new Color(0, 150, 150));
            ninjaButton.setBackground(null);
        });
        ninjaButton.addActionListener(e -> {
            selectedTowerType = TowerType.NINJA;
            ninjaButton.setBackground(new Color(0, 150, 150));
            normalButton.setBackground(null);
        });
        normalButton.setBackground(new Color(0, 150, 150)); // default selected
        selectPanel.add(new JLabel("Select Monkey:"));
        normalButton.setOpaque(true);
        normalButton.setBackground(new Color(40, 40, 40));
        normalButton.setForeground(new Color(255,255,255));
        normalButton.setFont(new Font("Segoe UI", Font.BOLD, 12));
        normalButton.setMargin(new java.awt.Insets(8, 12, 8, 12));
        normalButton.setBorder(javax.swing.BorderFactory.createCompoundBorder(
                javax.swing.BorderFactory.createLineBorder(new Color(100,100,100)),
                javax.swing.BorderFactory.createEmptyBorder(6, 8, 6, 8)
        ));

        ninjaButton.setOpaque(true);
        ninjaButton.setBackground(new Color(40, 40, 40));
        ninjaButton.setForeground(new Color(255,255,255));
        ninjaButton.setFont(new Font("Segoe UI", Font.BOLD, 12));
        ninjaButton.setMargin(new java.awt.Insets(8, 12, 8, 12));
        ninjaButton.setBorder(javax.swing.BorderFactory.createCompoundBorder(
                javax.swing.BorderFactory.createLineBorder(new Color(100,100,100)),
                javax.swing.BorderFactory.createEmptyBorder(6, 8, 6, 8)
        ));

        selectPanel.add(normalButton);
        selectPanel.add(ninjaButton);

        mainPanel.add(topBar, BorderLayout.NORTH);
        mainPanel.add(selectPanel, BorderLayout.WEST);
        mainPanel.add(gameCanvas, BorderLayout.CENTER);
        return mainPanel;
    }

    public void addTower(int x, int y) {
        int cost = getCostFor(selectedTowerType);
        if (money >= cost) {
            towers.add(new Tower(x, y, selectedTowerType));
            money -= cost;
            if (this.infoLabel != null) this.infoLabel.setText("Money: $" + money + " | Lives: " + lives + " | Round: " + round);
        }
    }

    public List<Tower> getTowers() {
        return towers;
    }

    public List<Balloon> getBalloons() {
        return balloons;
    }

    public GamePath getPath() {
        return gamePath;
    }

    public int getMoney() {
        return money;
    }

    public void setMoney(int amount) {
        this.money = amount;
    }

    private static class GameCanvas extends JPanel {
        private BTD5Game game;
        private javax.swing.Timer timer;
        private List<Particle> effects = new ArrayList<>();
        private List<Shuriken> shurikens = new ArrayList<>();
        private List<Pebble> pebbles = new ArrayList<>();
        private java.awt.image.BufferedImage normalSprite;
        private java.awt.image.BufferedImage ninjaSprite;

        // viewport transform (world -> screen)
        private double viewScale = 1.0;
        private double viewOffsetX = 0.0;
        private double viewOffsetY = 0.0;

        GameCanvas(BTD5Game game) {
            this.game = game;
            setBackground(new Color(20, 20, 20));
            setDoubleBuffered(true);

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    // convert screen to world coordinates
                    int sx = e.getX();
                    int sy = e.getY();
                    int wx = (int) ((sx - viewOffsetX) / viewScale);
                    int wy = (int) ((sy - viewOffsetY) / viewScale);

                    // Check if clicking sell button on selected tower
                    for (Tower t : game.getTowers()) {
                        if (t.selected) {
                            int btnX = (int) (t.getX() * viewScale + viewOffsetX) - 40;
                            int btnY = (int) (t.getY() * viewScale + viewOffsetY) + 30;
                            if (sx >= btnX && sx <= btnX + 80 && sy >= btnY && sy <= btnY + 24) {
                                // Sell the tower
                                game.money += getCostFor(t.type) / 2;
                                if (game.infoLabel != null) game.infoLabel.setText("Money: $" + game.money + " | Lives: " + game.lives + " | Round: " + game.round);
                                game.getTowers().remove(t);
                                repaint();
                                return;
                            }
                        }
                    }

                    // If clicking an existing tower -> select it and show range
                    for (Tower t : game.getTowers()) {
                        double dx = t.getX() - wx;
                        double dy = t.getY() - wy;
                        if (Math.hypot(dx, dy) <= 16) {
                            for (Tower ot : game.getTowers()) ot.selected = false;
                            t.selected = true;
                            repaint();
                            return;
                        }
                    }

                    // Otherwise try to place a tower (but not on the road)
                    if (game.isPointOnRoad(wx, wy)) {
                        if (game.infoLabel != null) {
                            String prev = game.infoLabel.getText();
                            game.infoLabel.setText("Cannot place on road");
                            new javax.swing.Timer(1500, ev -> {
                                game.infoLabel.setText(prev);
                                ((javax.swing.Timer) ev.getSource()).stop();
                            }).start();
                        }
                        return;
                    }

                    game.addTower(wx, wy);
                    repaint();
                }
            });

            // Generate simple sprites programmatically so we don't need external files
            normalSprite = createSprite(TowerType.NORMAL, 30);
            ninjaSprite = createSprite(TowerType.NINJA, 30);

            timer = new javax.swing.Timer(40, ev -> {
                updateGame();
                repaint();
            });
            timer.start();
        }

        private void updateGame() {
            List<int[]> points = game.getPath().getPoints();
            if (points.size() < 2) return;

            List<Balloon> toRemove = new ArrayList<>();
            for (Balloon b : new ArrayList<>(game.getBalloons())) {
                if (b.pathIndex >= points.size() - 1) {
                    // reached end: lose a life and schedule removal
                    toRemove.add(b);
                } else {
                    int[] p0 = points.get(b.pathIndex);
                    int[] p1 = points.get(b.pathIndex + 1);
                    b.progress += 0.03;
                    if (b.progress >= 1.0) {
                        b.progress = 0;
                        b.pathIndex++;
                    }
                    double t = b.progress;
                    b.x = (int) (p0[0] * (1 - t) + p1[0] * t);
                    b.y = (int) (p0[1] * (1 - t) + p1[1] * t);
                }
            }

            // Prevent balloons overlapping on the track by pushing trailing balloons back (optimized)
            if (!game.getBalloons().isEmpty()) {
                List<Balloon> sorted = new ArrayList<>(game.getBalloons());
                sorted.sort((a, b) -> Double.compare(b.pathIndex + b.progress, a.pathIndex + a.progress));
                double minDist = BALLOON_RADIUS * 2.5;
                for (int i = 1; i < sorted.size(); i++) {
                    Balloon lead = sorted.get(i - 1);
                    Balloon trail = sorted.get(i);
                    double dx = lead.x - trail.x;
                    double dy = lead.y - trail.y;
                    double dist = Math.hypot(dx, dy);
                    if (dist < minDist) {
                        // Gently push back (once, no loop) to avoid lag
                        trail.progress -= 0.015;
                        if (trail.progress < 0) {
                            if (trail.pathIndex > 0) {
                                trail.pathIndex--;
                                trail.progress = 0.85;
                            } else {
                                trail.progress = 0;
                            }
                        }
                        int[] tp0 = points.get(trail.pathIndex);
                        int[] tp1 = points.get(Math.min(trail.pathIndex + 1, points.size() - 1));
                        double t2 = trail.progress;
                        trail.x = (int) (tp0[0] * (1 - t2) + tp1[0] * t2);
                        trail.y = (int) (tp0[1] * (1 - t2) + tp1[1] * t2);
                    }
                }
            }

            for (Balloon b : toRemove) {
                game.getBalloons().remove(b);
            }

            // Process balloons that reached the end with a small visible delay
            for (Balloon b : new ArrayList<>(game.getBalloons())) {
                if (b.pathIndex >= points.size() - 1) {
                    if (!b.reachedEnd) {
                        b.reachedEnd = true;
                        b.endTimer = 6; // visible for ~6 ticks
                        game.lives = Math.max(0, game.lives - 1);
                        if (game.infoLabel != null) game.infoLabel.setText("Money: $" + game.money + " | Lives: " + game.lives + " | Round: " + game.round);
                    }
                    b.endTimer--;
                    if (b.endTimer <= 0) {
                        game.getBalloons().remove(b);
                    }
                }
            }
            // Towers attack: ninjas spawn shurikens, normals spawn pebbles
            for (Tower t : game.getTowers()) {
                if (t.cooldown > 0) {
                    t.cooldown--;
                } else {
                    Balloon nearest = null;
                    double bestDist = Double.MAX_VALUE;
                    for (Balloon b : game.getBalloons()) {
                        double dx = t.getX() - b.getX();
                        double dy = t.getY() - b.getY();
                        double dist = Math.hypot(dx, dy);
                        if (dist < t.range && dist < bestDist) {
                            bestDist = dist;
                            nearest = b;
                        }
                    }
                    if (nearest != null) {
                        if (t.type == TowerType.NINJA) {
                            shurikens.add(new Shuriken(t.getX(), t.getY(), nearest));
                            t.cooldown = 20; // ninja cooldown
                        } else { // NORMAL
                            pebbles.add(new Pebble(t.getX(), t.getY(), nearest));
                            t.cooldown = 30; // normal cooldown
                        }
                    }
                }
            }

            // Update shurikens
            List<Shuriken> deadS = new ArrayList<>();
            List<Balloon> popped = new ArrayList<>();
            for (Shuriken s : new ArrayList<>(shurikens)) {
                s.update();
                if (!s.alive) {
                    deadS.add(s);
                    if (s.target != null) popped.add(s.target);
                    // Create impact particles
                    for (int i = 0; i < 4; i++) {
                        double angle = (i / 4.0) * Math.PI * 2;
                        double vx = Math.cos(angle) * 2.0;
                        double vy = Math.sin(angle) * 2.0;
                        effects.add(new Particle(s.x, s.y, vx, vy, 8));
                    }
                }
            }
            shurikens.removeAll(deadS);

            // Update pebbles
            List<Pebble> deadP = new ArrayList<>();
            for (Pebble p : new ArrayList<>(pebbles)) {
                p.update();
                if (!p.alive) {
                    deadP.add(p);
                    if (p.target != null) popped.add(p.target);
                    // Create impact particles
                    for (int i = 0; i < 4; i++) {
                        double angle = (i / 4.0) * Math.PI * 2;
                        double vx = Math.cos(angle) * 2.0;
                        double vy = Math.sin(angle) * 2.0;
                        effects.add(new Particle(p.x, p.y, vx, vy, 6));
                    }
                }
            }
            pebbles.removeAll(deadP);

            // Award money and remove popped balloons (avoid double-counting)
            if (!popped.isEmpty()) {
                java.util.Set<Balloon> unique = new java.util.HashSet<>(popped);
                int count = unique.size();
                game.money += count * MONEY_PER_POP;
                if (game.infoLabel != null) game.infoLabel.setText("Money: $" + game.money + " | Lives: " + game.lives + " | Round: " + game.round);
                // Create pop effects at balloon positions before removal
                for (Balloon b : unique) {
                    int bx = b.getX();
                    int by = b.getY();
                    // Spawn multiple particles radiating outward
                    for (int i = 0; i < 8; i++) {
                        double angle = (i / 8.0) * Math.PI * 2;
                        double vx = Math.cos(angle) * 3.5;
                        double vy = Math.sin(angle) * 3.5;
                        effects.add(new Particle(bx, by, vx, vy, 12));
                    }
                    game.getBalloons().remove(b);
                }
            }

            // Update and decay effects
            for (Particle p : new ArrayList<>(effects)) {
                p.update();
            }
            effects.removeIf(effect -> --effect.ttl <= 0);

            // update active projectiles count so round thread can wait for them
            game.activeProjectiles = shurikens.size() + pebbles.size() + effects.size();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g;
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

// Draw path as a jagged polyline, but first compute and apply fit-to-view transform
            if (game.gamePath != null && game.gamePath.getPoints().size() > 1) {
                List<int[]> points = game.gamePath.getPoints();

                // compute world bounds
                int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
                for (int[] p : points) {
                    minX = Math.min(minX, p[0]); minY = Math.min(minY, p[1]);
                    maxX = Math.max(maxX, p[0]); maxY = Math.max(maxY, p[1]);
                }
                double pathW = Math.max(1, maxX - minX);
                double pathH = Math.max(1, maxY - minY);
                double availW = getWidth() - 80; // margins
                double availH = getHeight() - 80;
                viewScale = Math.min(availW / pathW, availH / pathH);
                viewScale = Math.min(viewScale, 1.0); // don't scale up too much
                viewOffsetX = (getWidth() - pathW * viewScale) / 2.0 - minX * viewScale;
                viewOffsetY = (getHeight() - pathH * viewScale) / 2.0 - minY * viewScale;

                // apply transform
                java.awt.geom.AffineTransform old = g2d.getTransform();
                g2d.translate(viewOffsetX, viewOffsetY);
                g2d.scale(viewScale, viewScale);

                Path2D path = new Path2D.Double();
                path.moveTo(points.get(0)[0], points.get(0)[1]);
                for (int i = 1; i < points.size(); i++) {
                    int[] p1 = points.get(i);
                    path.lineTo(p1[0], p1[1]);
                }

                g2d.setColor(new Color(220, 180, 110)); // brighter, higher-contrast road
                // keep stroke independent of zoom by dividing by scale
                float strokeWidth = (float)(44.0 / Math.max(0.0001, viewScale));
                g2d.setStroke(new BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2d.draw(path);

                // Draw "End of track" sign near the final point (in world coords)
                int[] endPoint = points.get(points.size() - 1);
                int signX = endPoint[0] + 12;
                int signY = endPoint[1] - 34;
                // post
                g2d.setColor(new Color(100, 60, 20));
                g2d.fillRect(signX - 6, signY + 18, 4, 20);
                // sign board
                g2d.setColor(new Color(240, 240, 240));
                g2d.fillRect(signX, signY, 80, 20);
                g2d.setColor(new Color(20, 20, 20));
                g2d.setFont(new Font("Segoe UI", Font.BOLD, 12));
                g2d.drawString("End of track", signX + 6, signY + 14);

                // restore transform so other screen elements (like HUD) draw normally
                g2d.setTransform(old);
                }

            // Draw balloons (smaller, solid red fill) in world space so they match the path
            java.awt.geom.AffineTransform oldBalloonTransform = g2d.getTransform();
            g2d.translate(viewOffsetX, viewOffsetY);
            g2d.scale(viewScale, viewScale);
            g2d.setColor(new Color(200, 20, 20));
            for (Balloon b : game.getBalloons()) {
                g2d.fillOval(b.getX() - BALLOON_RADIUS, b.getY() - BALLOON_RADIUS, BALLOON_RADIUS * 2, BALLOON_RADIUS * 2);
            }
            g2d.setTransform(oldBalloonTransform);

            // Draw towers (use sprites) within world transform
            // apply world transform so towers and projectiles align to path
            java.awt.geom.AffineTransform old = g2d.getTransform();
            g2d.translate(viewOffsetX, viewOffsetY);
            g2d.scale(viewScale, viewScale);
            for (Tower tower : game.getTowers()) {
                java.awt.image.BufferedImage sprite = tower.type == TowerType.NINJA ? ninjaSprite : normalSprite;
                int w = sprite.getWidth();
                int h = sprite.getHeight();
                g2d.drawImage(sprite, tower.getX() - w/2, tower.getY() - h/2, null);

                // If selected, draw range circle
                if (tower.selected) {
                    g2d.setColor(new Color(0, 150, 255, 40));
                    g2d.fillOval(tower.getX() - tower.range, tower.getY() - tower.range, tower.range * 2, tower.range * 2);
                    g2d.setColor(new Color(0, 150, 255, 140));
                    g2d.setStroke(new BasicStroke((float)(2.0 / Math.max(0.0001, viewScale))));
                    g2d.drawOval(tower.getX() - tower.range, tower.getY() - tower.range, tower.range * 2, tower.range * 2);
                }
            }
            g2d.setTransform(old);

            // Draw shurikens (animated) and pebbles using world transform
            java.awt.geom.AffineTransform old2 = g2d.getTransform();
            g2d.translate(viewOffsetX, viewOffsetY);
            g2d.scale(viewScale, viewScale);

            for (Shuriken s : shurikens) {
                g2d.setColor(new Color(230, 230, 230));
                g2d.fillOval((int)s.x - SHURIKEN_RADIUS, (int)s.y - SHURIKEN_RADIUS, SHURIKEN_RADIUS * 2, SHURIKEN_RADIUS * 2);
                // simple rotating cross (scaled stroke)
                g2d.setStroke(new BasicStroke((float)(2.0 / Math.max(0.0001, viewScale))));
                g2d.drawLine((int)s.x - 6, (int)s.y, (int)s.x + 6, (int)s.y);
                g2d.drawLine((int)s.x, (int)s.y - 6, (int)s.x, (int)s.y + 6);
            }

            // Draw pebbles (normal tower projectiles) - brown color
            for (Pebble p : pebbles) {
                g2d.setColor(new Color(139, 69, 19));
                g2d.fillOval((int)p.x - PEBBLE_RADIUS, (int)p.y - PEBBLE_RADIUS, PEBBLE_RADIUS * 2, PEBBLE_RADIUS * 2);
            }

            // Draw effects (explosion particles) in world space
            for (Particle effect : effects) {
                int alpha = Math.max(30, effect.ttl * 20);
                g2d.setColor(new Color(255, 200, 100, alpha));
                g2d.fillOval((int)effect.x - 4, (int)effect.y - 4, 8, 8);
            }
            g2d.setTransform(old2);

            // Draw instruction text
            g.setColor(new Color(220, 220, 220));
            g.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            g.drawString("Selected: " + game.selectedTowerType.name() + " ($" + getCostFor(game.selectedTowerType) + ") | Click to place Monkey", 10, getHeight() - 10);

            // Draw sell button if a tower is selected
            for (Tower tower : game.getTowers()) {
                if (tower.selected) {
                    int btnX = (int) (tower.getX() * viewScale + viewOffsetX) - 40;
                    int btnY = (int) (tower.getY() * viewScale + viewOffsetY) + 30;
                    g.setColor(new Color(200, 0, 0));
                    g.fillRect(btnX, btnY, 80, 24);
                    g.setColor(new Color(255, 255, 255));
                    g.setFont(new Font("Segoe UI", Font.BOLD, 11));
                    g.drawString("Sell ($" + (getCostFor(tower.type) / 2) + ")", btnX + 6, btnY + 16);
                    break;
                }
            }
        }

        private static class Particle {
            double x, y, vx, vy;
            int ttl;

            Particle(double x, double y, double vx, double vy, int ttl) {
                this.x = x;
                this.y = y;
                this.vx = vx;
                this.vy = vy;
                this.ttl = ttl;
            }

            void update() {
                x += vx;
                y += vy;
                vx *= 0.95; // friction
                vy *= 0.95;
            }
        }

        private static class Effect {
            int x1, y1, x2, y2, ttl;

            Effect(int x1, int y1, int x2, int y2, int ttl) {
                this.x1 = x1;
                this.y1 = y1;
                this.x2 = x2;
                this.y2 = y2;
                this.ttl = ttl;
            }
        }
    }

    private static class GamePath {
        private List<int[]> points = new ArrayList<>();

        void addPoint(int x, int y) {
            points.add(new int[]{x, y});
        }

        List<int[]> getPoints() {
            return points;
        }
    }

    // Return true if point lies on the drawn road (approx by stroking curve)
    public boolean isPointOnRoad(int x, int y) {
        if (gamePath == null || gamePath.getPoints().size() < 2) return false;
        List<int[]> points = gamePath.getPoints();
        Path2D path = new Path2D.Double();
        path.moveTo(points.get(0)[0], points.get(0)[1]);
        for (int i = 1; i < points.size(); i++) {
            int[] p0 = points.get(i - 1);
            int[] p1 = points.get(i);
            if (i < points.size() - 1) {
                int[] p2 = points.get(i + 1);
                int cpx = p1[0];
                int cpy = p1[1];
                int endx = (p1[0] + p2[0]) / 2;
                int endy = (p1[1] + p2[1]) / 2;
                path.quadTo(cpx, cpy, endx, endy);
            } else {
                path.lineTo(p1[0], p1[1]);
            }
        }
        java.awt.Stroke stroke = new BasicStroke(44, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
        java.awt.Shape road = stroke.createStrokedShape(path);
        return road.contains(x, y);
    }

    private static class Tower {
        private int x;
        private int y;
        private TowerType type;
        // simple cooldown so ninjas don't spam
        private int cooldown = 0;
        // selected state for showing range
        private boolean selected = false;
        // range in pixels
        private int range;

        Tower(int x, int y, TowerType type) {
            this.x = x;
            this.y = y;
            this.type = type;
            this.range = (type == TowerType.NINJA) ? 120 : 80;
        }

        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }
    }

    // Helper to create simple programmatic sprites
    private static java.awt.image.BufferedImage createSprite(TowerType type, int size) {
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        if (type == TowerType.NORMAL) {
            g.setColor(new Color(200, 150, 0));
            g.fillOval(2, 2, size-4, size-4);
            g.setColor(new Color(255, 200, 0));
            g.fillOval(size/4, size/4, size/2, size/2);
            g.setColor(new Color(120, 80, 0));
            g.drawString("M", size/2 - 4, size/2 + 4);
        } else {
            // ninja
            g.setColor(new Color(60, 60, 80));
            g.fillOval(2, 2, size-4, size-4);
            g.setColor(new Color(20, 20, 20));
            int eyeSize = 4;
            g.fillRect(size/2 - 6, size/2 - 2, eyeSize, eyeSize);
            g.fillRect(size/2 + 2, size/2 - 2, eyeSize, eyeSize);
            g.setColor(new Color(200, 200, 200));
            g.drawString("N", size/2 - 4, size/2 + 8);
        }
        g.dispose();
        return img;
    }

    // Shuriken projectile - targets a balloon
    private static class Shuriken {
        double x, y;
        Balloon target;
        double vx, vy;
        boolean alive = true;

        Shuriken(double x, double y, Balloon target) {
            this.x = x;
            this.y = y;
            this.target = target;
            double dx = target.getX() - x;
            double dy = target.getY() - y;
            double len = Math.hypot(dx, dy);
            if (len == 0) { vx = vy = 0; } else { vx = dx / len * 10; vy = dy / len * 10; }
        }

        void update() {
            if (!alive) return;
            x += vx; y += vy;
            if (target == null) { alive = false; return; }
            double dx = target.getX() - x;
            double dy = target.getY() - y;
            if (Math.hypot(dx, dy) < BALLOON_RADIUS + SHURIKEN_RADIUS + 15) {
                // hit (very forgiving collision to prevent misses)
                alive = false;
            }
        }
    }

    // Pebble projectile used by normal monkeys
    private static class Pebble {
        double x, y;
        Balloon target;
        double vx, vy;
        boolean alive = true;

        Pebble(double x, double y, Balloon target) {
            this.x = x;
            this.y = y;
            this.target = target;
            double dx = target.getX() - x;
            double dy = target.getY() - y;
            double len = Math.hypot(dx, dy);
            if (len == 0) { vx = vy = 0; } else { vx = dx / len * 9; vy = dy / len * 9; }
        }

        void update() {
            if (!alive) return;
            x += vx; y += vy;
            if (target == null) { alive = false; return; }
            double dx = target.getX() - x;
            double dy = target.getY() - y;
            if (Math.hypot(dx, dy) < BALLOON_RADIUS + PEBBLE_RADIUS + 15) {
                // hit (very forgiving collision to prevent misses)
                alive = false;
            }
        }
    }

    private static class Balloon {
        int x;
        int y;
        int pathIndex;
        double progress; // 0 to 1 along current segment
        boolean reachedEnd = false;
        int endTimer = 0;

        Balloon() {
            this.x = 0;
            this.y = 150;
            this.pathIndex = 0;
            this.progress = 0;
            this.reachedEnd = false;
            this.endTimer = 0;
        }

        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }
    }
}
