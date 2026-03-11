package tvgameboy.games.btd5;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GradientPaint;
import java.awt.Polygon;
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
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;

import tvgameboy.shared.Game;

public final class BTD5Game implements Game {
    private List<Tower> towers = new ArrayList<>();
    private List<Balloon> balloons = new ArrayList<>();
    private int money = 5000;
    private int lives = 20;
    private int round = 1;
    private boolean roundStarted = false;
    private boolean autoStartRounds = true;
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

    private enum BalloonType {
        STARTER(1, 1.0, 50, new Color(255, 192, 220), new Color(214, 56, 126)),
        SKY(2, 1.08, 65, new Color(168, 220, 255), new Color(52, 126, 214)),
        LIME(3, 1.15, 85, new Color(206, 246, 136), new Color(84, 184, 58));

        private final int health;
        private final double speedMultiplier;
        private final int reward;
        private final Color topColor;
        private final Color bottomColor;

        BalloonType(int health, double speedMultiplier, int reward, Color topColor, Color bottomColor) {
            this.health = health;
            this.speedMultiplier = speedMultiplier;
            this.reward = reward;
            this.topColor = topColor;
            this.bottomColor = bottomColor;
        }
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
    private static final int BALLOON_RADIUS = 14; // slightly smaller balloons
    private static final double BASE_BALLOON_SPEED = 2.6; // pixels per tick along path
    private static final double SPEED_PER_ROUND = 0.45;
    private static final int BASE_BALLOONS_PER_ROUND = 8;
    private static final int BALLOONS_PER_ROUND_INCREASE = 3;
    private static final int BASE_SPAWN_DELAY_MS = 500;
    private static final int SPAWN_DELAY_REDUCTION_PER_ROUND_MS = 55;
    private static final int MIN_SPAWN_DELAY_MS = 80;
    private static final int SHURIKEN_RADIUS = 4;
    private static final int PEBBLE_RADIUS = 5;
    private static final int UPGRADE_RANGE_COST = 800;
    private static final int UPGRADE_SPEED_COST = 900;
    private static final int UPGRADE_DAMAGE_COST = 1000;
    private static final int UPGRADE_RANGE_AMOUNT = 30;
    private static final int MIN_COOLDOWN_NINJA = 8;
    private static final int MIN_COOLDOWN_NORMAL = 12;
    private static final float ROAD_DRAW_WIDTH = 44f;
    private static final float ROAD_BLOCK_WIDTH = 34f;
    private static final double PATH_ARROW_SPACING = 195.0;
    private static final double PATH_ARROW_LENGTH = 28.0;
    private static final double PATH_ARROW_HEAD_LENGTH = 10.0;
    private static final double PATH_ARROW_HEAD_WIDTH = 11.0;
    private static final float PATH_ARROW_SHAFT_WIDTH = 4.0f;
    private static final long PATH_ARROW_BLINK_STEP_MS = 260L;
    private static final double PATH_ARROW_MIN_CENTER_DISTANCE = 95.0;


    @Override
    public JComponent getView(Runnable returnToMenu) {
        // Create a more complex path that includes a full loop before exiting.
        gamePath = new GamePath();
        // Start going right
        gamePath.addPoint(20, 200);
        gamePath.addPoint(120, 200);
        gamePath.addPoint(220, 200);
        gamePath.addPoint(320, 200);
        gamePath.addPoint(420, 200);
        gamePath.addPoint(520, 200);

        // Enter a wider loop from top-right, then travel clockwise.
        gamePath.addPoint(520, 380);
        gamePath.addPoint(260, 380);
        gamePath.addPoint(260, 100);
        gamePath.addPoint(700, 100);
        gamePath.addPoint(700, 380);
        gamePath.addPoint(260, 380); // loop closes here with larger spacing

        // Exit loop and finish to the right with extra separation from the loop.
        gamePath.addPoint(260, 520);
        gamePath.addPoint(420, 520);
        gamePath.addPoint(580, 520);
        gamePath.addPoint(740, 520);
        gamePath.addPoint(860, 520);



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
        startRoundButton.addActionListener(event -> startRound(startRoundButton));

        JToggleButton autoRoundButton = new JToggleButton("Auto Rounds: ON");
        autoRoundButton.setFont(new Font("Segoe UI", Font.BOLD, 12));
        autoRoundButton.setBackground(new Color(0, 120, 180));
        autoRoundButton.setForeground(new Color(255, 255, 255));
        autoRoundButton.setFocusPainted(false);
        autoRoundButton.setSelected(true);
        autoRoundButton.addActionListener(event -> {
            autoStartRounds = autoRoundButton.isSelected();
            autoRoundButton.setText(autoStartRounds ? "Auto Rounds: ON" : "Auto Rounds: OFF");
            autoRoundButton.setBackground(autoStartRounds ? new Color(0, 120, 180) : new Color(55, 55, 55));
            if (autoStartRounds && !roundStarted) {
                startRound(startRoundButton);
            }
        });

        topBar.add(menuButton, BorderLayout.WEST);
        topBar.add(infoPanel, BorderLayout.CENTER);
        JPanel roundControls = new JPanel();
        roundControls.setBackground(new Color(30, 30, 30));
        roundControls.add(autoRoundButton);
        roundControls.add(startRoundButton);
        topBar.add(roundControls, BorderLayout.EAST);

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
        SwingUtilities.invokeLater(() -> startRound(startRoundButton));
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

    private void startRound(JButton startRoundButton) {
        if (roundStarted || lives <= 0) {
            return;
        }
        roundStarted = true;
        startRoundButton.setEnabled(false);
        if (infoLabel != null) {
            infoLabel.setText("Money: $" + money + " | Lives: " + lives + " | Round: " + round + " (RUNNING)");
        }

        new Thread(() -> {
            try {
                int roundNumber = round;
                int balloonsToSpawn = getBalloonCountForRound(roundNumber);
                int batchSize = getSpawnBatchSizeForRound(roundNumber);
                int spawnDelayMs = getSpawnDelayForRound(roundNumber);

                for (int spawned = 0; spawned < balloonsToSpawn; ) {
                    int inBatch = Math.min(batchSize, balloonsToSpawn - spawned);
                    for (int j = 0; j < inBatch; j++) {
                        Balloon b = new Balloon(getBalloonTypeForSpawn(roundNumber, spawned, balloonsToSpawn));
                        if (gamePath.getPoints().size() > 0) {
                            int[] p = gamePath.getPoints().get(0);
                            b.x = p[0];
                            b.y = p[1];
                            b.displayX = b.x;
                            b.displayY = b.y;
                        }
                        synchronized (balloons) {
                            while (true) {
                                boolean tooClose = false;
                                for (Balloon ob : balloons) {
                                    double dx = ob.x - b.x;
                                    double dy = ob.y - b.y;
                                    if (Math.hypot(dx, dy) < getStartSpawnSeparationForRound(roundNumber)) {
                                        tooClose = true;
                                        break;
                                    }
                                }
                                if (!tooClose) break;
                                Thread.sleep(40);
                            }
                            balloons.add(b);
                        }
                        spawned++;
                    }
                    Thread.sleep(spawnDelayMs);
                }

                while (true) {
                    synchronized (balloons) {
                        if (balloons.isEmpty() && activeProjectiles == 0) break;
                    }
                    Thread.sleep(200);
                }

                roundStarted = false;
                round++;
                money += 500;
                SwingUtilities.invokeLater(() -> {
                    startRoundButton.setEnabled(true);
                    if (infoLabel != null) {
                        infoLabel.setText("Money: $" + money + " | Lives: " + lives + " | Round: " + round);
                    }
                    if (autoStartRounds && lives > 0) {
                        startRound(startRoundButton);
                    }
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "btd5-round-runner").start();
    }

    private static class GameCanvas extends JPanel {
        private BTD5Game game;
        private javax.swing.Timer timer;
        private List<Particle> effects = new ArrayList<>();
        private List<Shuriken> shurikens = new ArrayList<>();
        private List<Pebble> pebbles = new ArrayList<>();
        private java.awt.image.BufferedImage starterBalloonSprite;
        private java.awt.image.BufferedImage skyBalloonSprite;
        private java.awt.image.BufferedImage limeBalloonSprite;
        private java.awt.image.BufferedImage normalSprite;
        private java.awt.image.BufferedImage ninjaSprite;

        // viewport transform (world -> screen)
        private double viewScale = 1.0;
        private double viewOffsetX = 0.0;
        private double viewOffsetY = 0.0;

        GameCanvas(BTD5Game game) {
            this.game = game;
            setBackground(new Color(86, 148, 67));
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
                            int upX = btnX;
                            int upY = btnY + 30;
                            if (sx >= upX && sx <= upX + 80 && sy >= upY && sy <= upY + 24) {
                                // Upgrade range
                                if (!t.rangeUpgraded && game.money >= UPGRADE_RANGE_COST) {
                                    game.money -= UPGRADE_RANGE_COST;
                                    t.range += UPGRADE_RANGE_AMOUNT;
                                    t.rangeUpgraded = true;
                                    if (game.infoLabel != null) game.infoLabel.setText("Money: $" + game.money + " | Lives: " + game.lives + " | Round: " + game.round);
                                    repaint();
                                } else if (t.rangeUpgraded && game.infoLabel != null) {
                                    game.infoLabel.setText("Range already upgraded");
                                } else if (game.infoLabel != null) {
                                    game.infoLabel.setText("Not enough money for Range");
                                }
                                return;
                            }
                            int spdX = btnX;
                            int spdY = btnY + 60;
                            if (sx >= spdX && sx <= spdX + 80 && sy >= spdY && sy <= spdY + 24) {
                                // Upgrade fire speed
                                if (!t.speedUpgraded && game.money >= UPGRADE_SPEED_COST) {
                                    game.money -= UPGRADE_SPEED_COST;
                                    t.baseCooldown = Math.max(t.getMinCooldown(), (int) Math.round(t.baseCooldown * 0.8));
                                    t.speedUpgraded = true;
                                    if (game.infoLabel != null) game.infoLabel.setText("Money: $" + game.money + " | Lives: " + game.lives + " | Round: " + game.round);
                                    repaint();
                                } else if (t.speedUpgraded && game.infoLabel != null) {
                                    game.infoLabel.setText("Speed already upgraded");
                                } else if (game.infoLabel != null) {
                                    game.infoLabel.setText("Not enough money for Speed");
                                }
                                return;
                            }
                            int dmgX = btnX;
                            int dmgY = btnY + 90;
                            if (sx >= dmgX && sx <= dmgX + 80 && sy >= dmgY && sy <= dmgY + 24) {
                                // Upgrade damage
                                if (!t.damageUpgraded && game.money >= UPGRADE_DAMAGE_COST) {
                                    game.money -= UPGRADE_DAMAGE_COST;
                                    t.damage += 1;
                                    t.damageUpgraded = true;
                                    if (game.infoLabel != null) game.infoLabel.setText("Money: $" + game.money + " | Lives: " + game.lives + " | Round: " + game.round);
                                    repaint();
                                } else if (t.damageUpgraded && game.infoLabel != null) {
                                    game.infoLabel.setText("Damage already upgraded");
                                } else if (game.infoLabel != null) {
                                    game.infoLabel.setText("Not enough money for Damage");
                                }
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
                    for (Tower ot : game.getTowers()) ot.selected = false;
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
            // But prefer an embedded photo resource for the normal monkey if available
            java.awt.image.BufferedImage loadedNormal = null;
            try {
                java.io.InputStream is = BTD5Game.class.getResourceAsStream("/monkey_normal.png");
                if (is != null) {
                    loadedNormal = javax.imageio.ImageIO.read(is);
                }
            } catch (Exception ex) {
                // ignore - fallback to generated sprite
            }
            if (loadedNormal != null) {
                normalSprite = new java.awt.image.BufferedImage(30, 30, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                java.awt.Graphics2D g2 = normalSprite.createGraphics();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.drawImage(loadedNormal, 0, 0, 30, 30, null);
                g2.dispose();
            } else {
                normalSprite = createSprite(TowerType.NORMAL, 30);
            }
            starterBalloonSprite = createBalloonSprite(BALLOON_RADIUS * 3, BALLOON_RADIUS * 4, BalloonType.STARTER);
            skyBalloonSprite = createBalloonSprite(BALLOON_RADIUS * 3, BALLOON_RADIUS * 4, BalloonType.SKY);
            limeBalloonSprite = createBalloonSprite(BALLOON_RADIUS * 3, BALLOON_RADIUS * 4, BalloonType.LIME);
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
            double[] segLen = new double[points.size() - 1];
            double[] cumLen = new double[points.size()];
            for (int i = 0; i < points.size() - 1; i++) {
                int[] p0 = points.get(i);
                int[] p1 = points.get(i + 1);
                segLen[i] = Math.hypot(p1[0] - p0[0], p1[1] - p0[1]);
                cumLen[i + 1] = cumLen[i] + segLen[i];
            }
            double totalLen = cumLen[cumLen.length - 1];

            List<Balloon> toRemove = new ArrayList<>();
            for (Balloon b : new ArrayList<>(game.getBalloons())) {
                if (b.distance >= totalLen) {
                    // reached end: lose a life and schedule removal
                    toRemove.add(b);
                } else {
                    b.distance += getBalloonSpeedForRound(game.round) * b.type.speedMultiplier;
                    if (b.distance > totalLen) b.distance = totalLen;
                    setBalloonPositionFromDistance(b, points, segLen, cumLen);
                    // Smooth display position to avoid visual jitter on turns or nudges
                    b.displayX += (b.x - b.displayX) * 0.25;
                    b.displayY += (b.y - b.displayY) * 0.25;
                }
            }

            // Prevent balloons overlapping on the track by spacing along path distance (no jitter/backtracking)
            if (!game.getBalloons().isEmpty()) {
                List<Balloon> sorted = new ArrayList<>(game.getBalloons());
                sorted.sort((a, b) -> Double.compare(b.distance, a.distance));
                double minDist = getBalloonSpacingForRound(game.round);
                for (int i = 1; i < sorted.size(); i++) {
                    Balloon lead = sorted.get(i - 1);
                    Balloon trail = sorted.get(i);
                    double desired = lead.distance - minDist;
                    if (trail.distance > desired) {
                        trail.distance = Math.max(0, desired);
                        setBalloonPositionFromDistance(trail, points, segLen, cumLen);
                    }
                }
            }

            for (Balloon b : toRemove) {
                game.getBalloons().remove(b);
            }

            // Process balloons that reached the end with a small visible delay
            for (Balloon b : new ArrayList<>(game.getBalloons())) {
                if (b.distance >= totalLen) {
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
                            shurikens.add(new Shuriken(t.getX(), t.getY(), nearest, t.damage));
                            t.cooldown = t.baseCooldown;
                        } else { // NORMAL
                            pebbles.add(new Pebble(t.getX(), t.getY(), nearest, t.damage));
                            t.cooldown = t.baseCooldown;
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
                    if (s.target != null && s.target.isDead()) popped.add(s.target);
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
                    if (p.target != null && p.target.isDead()) popped.add(p.target);
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
                int reward = 0;
                // Create pop effects at balloon positions before removal
                for (Balloon b : unique) {
                    reward += b.type.reward;
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
                game.money += reward;
                if (game.infoLabel != null) game.infoLabel.setText("Money: $" + game.money + " | Lives: " + game.lives + " | Round: " + game.round);
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

            // Grass-like background
            g2d.setPaint(new GradientPaint(0, 0, new Color(98, 168, 75), 0, getHeight(), new Color(70, 130, 55)));
            g2d.fillRect(0, 0, getWidth(), getHeight());
            g2d.setColor(new Color(120, 185, 95, 40));
            for (int y = 0; y < getHeight(); y += 28) {
                g2d.fillRect(0, y, getWidth(), 12);
            }

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
                float strokeWidth = (float)(ROAD_DRAW_WIDTH / Math.max(0.0001, viewScale));
                g2d.setStroke(new BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2d.draw(path);

                drawEvenlySpacedPathArrows(g2d, points);

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

            // Draw balloons as a sprite in world space so they match the path
            java.awt.geom.AffineTransform oldBalloonTransform = g2d.getTransform();
            g2d.translate(viewOffsetX, viewOffsetY);
            g2d.scale(viewScale, viewScale);
            for (Balloon b : game.getBalloons()) {
                java.awt.image.BufferedImage balloonSprite = getBalloonSprite(b.type);
                int spriteX = (int) Math.round(b.displayX - balloonSprite.getWidth() / 2.0);
                int spriteY = (int) Math.round(b.displayY - balloonSprite.getHeight() * 0.68);
                g2d.drawImage(balloonSprite, spriteX, spriteY, null);
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
                    g2d.setColor(new Color(0, 170, 255, 70));
                    g2d.fillOval(tower.getX() - tower.range, tower.getY() - tower.range, tower.range * 2, tower.range * 2);
                    g2d.setColor(new Color(0, 170, 255, 200));
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
                    int upX = btnX;
                    int upY = btnY + 30;
                    g.setColor(tower.rangeUpgraded ? new Color(90, 90, 90) : new Color(0, 120, 200));
                    g.fillRect(upX, upY, 80, 24);
                    g.setColor(new Color(255, 255, 255));
                    g.drawString(tower.rangeUpgraded ? "Range (MAX)" : "Range ($" + UPGRADE_RANGE_COST + ")", upX + 6, upY + 16);
                    int spdX = btnX;
                    int spdY = btnY + 60;
                    g.setColor(tower.speedUpgraded ? new Color(90, 90, 90) : new Color(200, 120, 0));
                    g.fillRect(spdX, spdY, 80, 24);
                    g.setColor(new Color(255, 255, 255));
                    g.drawString(tower.speedUpgraded ? "Speed (MAX)" : "Speed ($" + UPGRADE_SPEED_COST + ")", spdX + 6, spdY + 16);
                    int dmgX = btnX;
                    int dmgY = btnY + 90;
                    g.setColor(tower.damageUpgraded ? new Color(90, 90, 90) : new Color(160, 60, 200));
                    g.fillRect(dmgX, dmgY, 80, 24);
                    g.setColor(new Color(255, 255, 255));
                    g.drawString(tower.damageUpgraded ? "Dmg (MAX)" : "Dmg ($" + UPGRADE_DAMAGE_COST + ")", dmgX + 6, dmgY + 16);
                    break;
                }
            }
        }

        private java.awt.image.BufferedImage getBalloonSprite(BalloonType type) {
            if (type == BalloonType.SKY) {
                return skyBalloonSprite;
            }
            if (type == BalloonType.LIME) {
                return limeBalloonSprite;
            }
            return starterBalloonSprite;
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

    // Return true if point lies directly on the road polyline.
    public boolean isPointOnRoad(int x, int y) {
        if (gamePath == null || gamePath.getPoints().size() < 2) return false;
        List<int[]> points = gamePath.getPoints();
        double halfWidth = ROAD_BLOCK_WIDTH / 2.0;
        double maxDistSq = halfWidth * halfWidth;
        for (int i = 0; i < points.size() - 1; i++) {
            int[] p0 = points.get(i);
            int[] p1 = points.get(i + 1);
            if (distancePointToSegmentSq(x, y, p0[0], p0[1], p1[0], p1[1]) <= maxDistSq) {
                return true;
            }
        }
        return false;
    }

    private static double distancePointToSegmentSq(double px, double py, double x1, double y1, double x2, double y2) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        if (dx == 0 && dy == 0) {
            double ddx = px - x1;
            double ddy = py - y1;
            return ddx * ddx + ddy * ddy;
        }
        double t = ((px - x1) * dx + (py - y1) * dy) / (dx * dx + dy * dy);
        t = Math.max(0, Math.min(1, t));
        double projX = x1 + t * dx;
        double projY = y1 + t * dy;
        double ddx = px - projX;
        double ddy = py - projY;
        return ddx * ddx + ddy * ddy;
    }

    private static void drawEvenlySpacedPathArrows(Graphics2D g2d, List<int[]> points) {
        if (points.size() < 2) return;
        double[] segLen = new double[points.size() - 1];
        double[] cumLen = new double[points.size()];
        for (int i = 0; i < points.size() - 1; i++) {
            int[] p0 = points.get(i);
            int[] p1 = points.get(i + 1);
            segLen[i] = Math.hypot(p1[0] - p0[0], p1[1] - p0[1]);
            cumLen[i + 1] = cumLen[i] + segLen[i];
        }
        double totalLen = cumLen[cumLen.length - 1];
        if (totalLen < PATH_ARROW_SPACING * 0.5) return;

        long tick = System.currentTimeMillis() / PATH_ARROW_BLINK_STEP_MS;
        int seg = 0;
        int arrowIndex = 0;
        List<double[]> centers = new ArrayList<>();
        for (double d = PATH_ARROW_SPACING * 0.5; d <= totalLen - PATH_ARROW_SPACING * 0.2; d += PATH_ARROW_SPACING) {
            while (seg < segLen.length - 1 && d > cumLen[seg + 1]) seg++;
            double len = Math.max(0.0001, segLen[seg]);
            double t = (d - cumLen[seg]) / len;
            int[] p0 = points.get(seg);
            int[] p1 = points.get(seg + 1);
            double ax = p0[0] + (p1[0] - p0[0]) * t;
            double ay = p0[1] + (p1[1] - p0[1]) * t;
            double dx = p1[0] - p0[0];
            double dy = p1[1] - p0[1];

            boolean overlaps = false;
            for (double[] c : centers) {
                if (Math.hypot(c[0] - ax, c[1] - ay) < PATH_ARROW_MIN_CENTER_DISTANCE) {
                    overlaps = true;
                    break;
                }
            }
            if (overlaps) continue;
            centers.add(new double[]{ax, ay});

            boolean lit = Math.floorMod((int) (tick - arrowIndex), 4) == 0;
            g2d.setColor(lit ? new Color(92, 76, 55, 112) : new Color(92, 76, 55, 52));
            drawRoadArrow(g2d, ax, ay, dx, dy, PATH_ARROW_LENGTH, PATH_ARROW_HEAD_LENGTH, PATH_ARROW_HEAD_WIDTH, PATH_ARROW_SHAFT_WIDTH);
            arrowIndex++;
        }
    }

    private static void drawRoadArrow(Graphics2D g2d, double x, double y, double dx, double dy, double len, double headLen, double headWidth, float shaftWidth) {
        double mag = Math.hypot(dx, dy);
        if (mag < 0.0001) return;
        double ux = dx / mag;
        double uy = dy / mag;
        double px = -uy;
        double py = ux;

        double tipXf = x + ux * len * 0.5;
        double tipYf = y + uy * len * 0.5;
        double headBaseXf = tipXf - ux * headLen;
        double headBaseYf = tipYf - uy * headLen;
        double tailXf = x - ux * len * 0.5;
        double tailYf = y - uy * len * 0.5;

        java.awt.Stroke oldStroke = g2d.getStroke();
        g2d.setStroke(new BasicStroke(shaftWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2d.drawLine((int) Math.round(tailXf), (int) Math.round(tailYf), (int) Math.round(headBaseXf), (int) Math.round(headBaseYf));
        g2d.setStroke(oldStroke);

        int tipX = (int) Math.round(tipXf);
        int tipY = (int) Math.round(tipYf);
        int leftX = (int) Math.round(headBaseXf + px * headWidth * 0.5);
        int leftY = (int) Math.round(headBaseYf + py * headWidth * 0.5);
        int rightX = (int) Math.round(headBaseXf - px * headWidth * 0.5);
        int rightY = (int) Math.round(headBaseYf - py * headWidth * 0.5);

        Polygon arrow = new Polygon();
        arrow.addPoint(tipX, tipY);
        arrow.addPoint(leftX, leftY);
        arrow.addPoint(rightX, rightY);
        g2d.fillPolygon(arrow);
    }

    private static class Tower {
        private int x;
        private int y;
        private TowerType type;
        // simple cooldown so ninjas don't spam
        private int cooldown = 0;
        private int baseCooldown;
        private int damage = 1;
        private boolean rangeUpgraded = false;
        private boolean speedUpgraded = false;
        private boolean damageUpgraded = false;
        // selected state for showing range
        private boolean selected = false;
        // range in pixels
        private int range;

        Tower(int x, int y, TowerType type) {
            this.x = x;
            this.y = y;
            this.type = type;
            this.range = (type == TowerType.NINJA) ? 120 : 80;
            this.baseCooldown = (type == TowerType.NINJA) ? 14 : 20;
        }

        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }

        public int getMinCooldown() {
            return (type == TowerType.NINJA) ? MIN_COOLDOWN_NINJA : MIN_COOLDOWN_NORMAL;
        }
    }

    private static double getBalloonSpeedForRound(int round) {
        return BASE_BALLOON_SPEED + Math.max(0, round - 1) * SPEED_PER_ROUND;
    }

    private static int getBalloonCountForRound(int round) {
        return BASE_BALLOONS_PER_ROUND + Math.max(0, round - 1) * BALLOONS_PER_ROUND_INCREASE;
    }

    private static int getSpawnDelayForRound(int round) {
        int delay = BASE_SPAWN_DELAY_MS - Math.max(0, round - 1) * SPAWN_DELAY_REDUCTION_PER_ROUND_MS;
        return Math.max(MIN_SPAWN_DELAY_MS, delay);
    }

    private static int getSpawnBatchSizeForRound(int round) {
        return Math.min(7, 2 + Math.max(0, round - 1) / 2);
    }

    private static double getBalloonSpacingForRound(int round) {
        double spacing = BALLOON_RADIUS * 2.4 - Math.max(0, round - 1) * 1.8;
        return Math.max(BALLOON_RADIUS * 1.15, spacing);
    }

    private static double getStartSpawnSeparationForRound(int round) {
        double spacing = BALLOON_RADIUS * 3.4 - Math.max(0, round - 1) * 2.0;
        return Math.max(BALLOON_RADIUS * 1.25, spacing);
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

    private static java.awt.image.BufferedImage createBalloonSprite(int width, int height, BalloonType type) {
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int bodyWidth = Math.max(16, (int) Math.round(width * 0.68));
        int bodyHeight = Math.max(20, (int) Math.round(height * 0.72));
        int bodyX = (width - bodyWidth) / 2;
        int bodyY = 2;

        g.setPaint(new GradientPaint(
                bodyX,
                bodyY,
                type.topColor,
                bodyX + bodyWidth,
                bodyY + bodyHeight,
                type.bottomColor
        ));
        g.fillOval(bodyX, bodyY, bodyWidth, bodyHeight);

        g.setColor(type.bottomColor.darker());
        g.setStroke(new BasicStroke(2f));
        g.drawOval(bodyX, bodyY, bodyWidth, bodyHeight);

        g.setColor(new Color(255, 255, 255, 150));
        g.fillOval(bodyX + bodyWidth / 5, bodyY + bodyHeight / 7, bodyWidth / 4, bodyHeight / 3);

        int knotTopY = bodyY + bodyHeight - 2;
        Polygon knot = new Polygon();
        knot.addPoint(width / 2 - 4, knotTopY);
        knot.addPoint(width / 2 + 4, knotTopY);
        knot.addPoint(width / 2, knotTopY + 8);
        g.setColor(type.bottomColor.darker());
        g.fillPolygon(knot);

        g.setColor(new Color(125, 92, 108));
        g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D.Double string = new Path2D.Double();
        string.moveTo(width / 2.0, knotTopY + 7.0);
        string.curveTo(width / 2.0 - 3.5, knotTopY + 13.0, width / 2.0 + 2.0, height - 10.0, width / 2.0 - 1.0, height - 2.0);
        g.draw(string);

        g.dispose();
        return img;
    }

    // Shuriken projectile - targets a balloon
    private static class Shuriken {
        double x, y;
        Balloon target;
        int damage;
        double vx, vy;
        boolean alive = true;

        Shuriken(double x, double y, Balloon target, int damage) {
            this.x = x;
            this.y = y;
            this.target = target;
            this.damage = damage;
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
                target.applyDamage(damage);
                alive = false;
            }
        }
    }

    // Pebble projectile used by normal monkeys
    private static class Pebble {
        double x, y;
        Balloon target;
        int damage;
        double vx, vy;
        boolean alive = true;

        Pebble(double x, double y, Balloon target, int damage) {
            this.x = x;
            this.y = y;
            this.target = target;
            this.damage = damage;
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
                target.applyDamage(damage);
                alive = false;
            }
        }
    }

    private static class Balloon {
        BalloonType type;
        double x;
        double y;
        // display coordinates used for smooth rendering (interpolated)
        double displayX;
        double displayY;
        int pathIndex;
        double progress; // 0 to 1 along current segment
        double distance; // distance along path in pixels
        int health;
        boolean reachedEnd = false;
        int endTimer = 0;

        Balloon(BalloonType type) {
            this.type = type;
            this.x = 0;
            this.y = 150;
            this.pathIndex = 0;
            this.progress = 0;
            this.distance = 0;
            this.health = type.health;
            this.reachedEnd = false;
            this.endTimer = 0;
            this.displayX = this.x;
            this.displayY = this.y;
        }

        public int getX() {
            return (int) Math.round(x);
        }

        public int getY() {
            return (int) Math.round(y);
        }

        public void applyDamage(int dmg) {
            this.health -= Math.max(1, dmg);
        }

        public boolean isDead() {
            return this.health <= 0;
        }
    }

    private static void setBalloonPositionFromDistance(Balloon b, List<int[]> points, double[] segLen, double[] cumLen) {
        double d = Math.max(0, b.distance);
        int seg = 0;
        while (seg < segLen.length - 1 && d > cumLen[seg + 1]) {
            seg++;
        }
        double segStart = cumLen[seg];
        double segDistance = segLen[seg] == 0 ? 1.0 : segLen[seg];
        double t = (d - segStart) / segDistance;
        int[] p0 = points.get(seg);
        int[] p1 = points.get(seg + 1);
        b.pathIndex = seg;
        b.progress = Math.max(0, Math.min(1, t));
        b.x = (p0[0] * (1 - b.progress) + p1[0] * b.progress);
        b.y = (p0[1] * (1 - b.progress) + p1[1] * b.progress);
    }

    private static BalloonType getBalloonTypeForSpawn(int round, int spawned, int totalToSpawn) {
        double progress = totalToSpawn <= 1 ? 1.0 : (double) spawned / (double) (totalToSpawn - 1);
        if (round >= 5) {
            if (progress > 0.72 || spawned % 6 == 5) {
                return BalloonType.LIME;
            }
            if (progress > 0.35 || spawned % 4 == 3) {
                return BalloonType.SKY;
            }
        }
        if (round >= 3 && (progress > 0.45 || spawned % 5 == 4)) {
            return BalloonType.SKY;
        }
        return BalloonType.STARTER;
    }
}
