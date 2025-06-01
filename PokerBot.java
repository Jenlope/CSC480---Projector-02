import java.util.*;

public class PokerBot {

    static final String[] RANKS = {"2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K", "A"};
    static final String[] SUITS = {"♠", "♥", "♦", "♣"};
    static final List<String> FULL_DECK = createDeck();
    static final int TIME_LIMIT_MS = 10000;

    public static void main(String[] args) {
        List<String> myCards = Arrays.asList("A♠", "K♥");
        List<String> tableCards = new ArrayList<>();
        System.out.println("Decision Rule: " + decideAction(myCards, tableCards));
    }

    static class Node {
        int wins = 0, visits = 0;
        Node parent;
        List<Node> children = new ArrayList<>();

        Node(Node parent) {
            this.parent = parent;
        }

        double ucb1() {
            return visits == 0
                    ? Double.POSITIVE_INFINITY
                    : (double) wins / visits + Math.sqrt(2 * Math.log(parent.visits) / visits);
        }

        Node selectBestChild() {
            return children.stream().max(Comparator.comparingDouble(Node::ucb1)).orElse(null);
        }

        Node expand() {
            Node child = new Node(this);
            children.add(child);
            return child;
        }

        void update(boolean win) {
            visits++;
            if (win) wins++;
        }
    }

    public static String decideAction(List<String> myCards, List<String> tableCards) {
        long start = System.currentTimeMillis();
        Node root = new Node(null);

        Set<String> seenCards = new HashSet<>(myCards);
        seenCards.addAll(tableCards);

        while (System.currentTimeMillis() - start < TIME_LIMIT_MS) {
            Node node = root;
            while (!node.children.isEmpty()) {
                node = node.selectBestChild();
            }
            Node child = node.expand();
            boolean win = simulate(myCards, tableCards, seenCards);
            while (child != null) {
                child.update(win);
                child = child.parent;
            }
        }

        int wins = root.wins;
        int losses = root.visits - wins;
        double winRate = (double) wins / root.visits;

        System.out.println("Selection Policy");
        System.out.println("Total Simulations: " + root.visits);
        System.out.println("Wins: " + wins);
        System.out.println("Losses: " + losses);
        System.out.printf("Win Probability: %.2f%%\n", 100.0 * winRate);
        System.out.print("\n");

        return winRate >= 0.5 ? "Stay" : "Fold";
    }

    static boolean simulate(List<String> myCards, List<String> tableCards, Set<String> seenCards) {
        List<String> deck = new ArrayList<>(FULL_DECK);
        deck.removeAll(seenCards);
        Collections.shuffle(deck);

        List<String> oppCards = deck.subList(0, 2);
        System.out.println("Random Rollouts");
        System.out.println("Simulated random possible opponent hole cards: " + oppCards);

        int needed = 5 - tableCards.size();
        List<String> futureCards = deck.subList(2, 2 + needed);
        System.out.println("Simulated random future community cards: " + futureCards);

        List<String> board = new ArrayList<>(tableCards);
        board.addAll(futureCards);
        System.out.println("Play out to showdown randomly: " + board);

        int result = compare(myCards, oppCards, board);
        String outcome = result > 0 ? "Win" : result == 0 ? "Tie" : "Loss";
        System.out.println("Showdown random result: " + outcome + "\n");

        return result >= 0;
    }

    public static int compare(List<String> hand1, List<String> hand2, List<String> board) {
        List<String> full1 = new ArrayList<>(hand1), full2 = new ArrayList<>(hand2);
        full1.addAll(board);
        full2.addAll(board);
        return evaluate(full1).compareTo(evaluate(full2));
    }

    public static Hand evaluate(List<String> cards) {
        int[] rankCounts = new int[13], suitCounts = new int[4];
        List<Integer> ranks = new ArrayList<>();
        Map<Integer, List<Integer>> suitToRanks = new HashMap<>();

        for (String card : cards) {
            int rank = getValue(card), suit = getSuit(card);
            rankCounts[rank]++;
            suitCounts[suit]++;
            ranks.add(rank);
            suitToRanks.computeIfAbsent(suit, k -> new ArrayList<>()).add(rank);
        }

        boolean isFlush = false;
        List<Integer> flushRanks = null;
        for (List<Integer> suitRanks : suitToRanks.values()) {
            if (suitRanks.size() >= 5) {
                isFlush = true;
                flushRanks = suitRanks;
                break;
            }
        }

        Integer straightHigh = getStraightHigh(ranks);
        Integer flushStraightHigh = isFlush ? getStraightHigh(flushRanks) : null;

        if (Objects.equals(flushStraightHigh, 12)) return new Hand(9);
        if (flushStraightHigh != null) return new Hand(8, flushStraightHigh);

        for (int i = 12; i >= 0; i--) {
            if (rankCounts[i] == 4)
                return new Hand(7, i, getHighestExcluding(ranks, i));
        }

        Integer triple = null;
        List<Integer> pairs = new ArrayList<>();
        for (int i = 12; i >= 0; i--) {
            if (rankCounts[i] == 3 && triple == null) triple = i;
            else if (rankCounts[i] >= 2) pairs.add(i);
        }

        if (triple != null && !pairs.isEmpty()) return new Hand(6, triple, pairs.get(0));

        if (isFlush) {
            List<Integer> topFlush = flushRanks.stream()
                    .sorted(Comparator.reverseOrder())
                    .limit(5)
                    .toList();
            return new Hand(5, topFlush);
        }

        if (straightHigh != null) return new Hand(4, straightHigh);

        if (triple != null) {
            List<Integer> card = getTop(ranks, 2, triple);
            return new Hand(3, triple, card.get(0), card.get(1));
        }

        if (pairs.size() >= 2)
            return new Hand(2, pairs.get(0), pairs.get(1),
                    getHighestExcluding(ranks, pairs.get(0), pairs.get(1)));

        if (!pairs.isEmpty()) {
            List<Integer> card = getTop(ranks, 3, pairs.get(0));
            return new Hand(1, pairs.get(0), card.get(0), card.get(1), card.get(2));
        }

        ranks.sort(Comparator.reverseOrder());
        return new Hand(0, ranks.subList(0, 5));
    }

    static List<Integer> getTop(List<Integer> ranks, int count, int... exclude) {
        Set<Integer> excludeSet = new HashSet<>();
        for (int x : exclude) excludeSet.add(x);
        return ranks.stream()
                .filter(r -> !excludeSet.contains(r))
                .sorted(Comparator.reverseOrder())
                .limit(count)
                .toList();
    }

    static int getHighestExcluding(List<Integer> ranks, int... exclude) {
        Set<Integer> excludeSet = new HashSet<>();
        for (int x : exclude) excludeSet.add(x);
        return ranks.stream()
                .filter(r -> !excludeSet.contains(r))
                .max(Integer::compare)
                .orElse(0);
    }

    static Integer getStraightHigh(List<Integer> rawRanks) {
        Set<Integer> unique = new HashSet<>(rawRanks);
        for (int high = 12; high >= 4; high--) {
            boolean valid = true;
            for (int i = 0; i < 5; i++) {
                if (!unique.contains((high - i) % 13)) {
                    valid = false;
                    break;
                }
            }
            if (valid) return high;
        }
        return unique.containsAll(Arrays.asList(12, 0, 1, 2, 3)) ? 3 : null;
    }

    static int getValue(String card) {
        return switch (card.substring(0, card.length() - 1)) {
            case "2"  -> 0;
            case "3"  -> 1;
            case "4"  -> 2;
            case "5"  -> 3;
            case "6"  -> 4;
            case "7"  -> 5;
            case "8"  -> 6;
            case "9"  -> 7;
            case "10" -> 8;
            case "J"  -> 9;
            case "Q"  -> 10;
            case "K"  -> 11;
            case "A"  -> 12;
            default   -> throw new IllegalArgumentException("Invalid rank");
        };
    }

    static int getSuit(String card) {
        return switch (card.charAt(card.length() - 1)) {
            case '♠' -> 0;
            case '♥' -> 1;
            case '♦' -> 2;
            case '♣' -> 3;
            default  -> -1;
        };
    }

    public static List<String> createDeck() {
        List<String> deck = new ArrayList<>();
        for (String r : RANKS) {
            for (String s : SUITS) {
                deck.add(r + s);
            }
        }
        return deck;
    }

    static class Hand implements Comparable<Hand> {
        List<Integer> rank;

        Hand(int... values) {
            rank = new ArrayList<>();
            for (int v : values) rank.add(v);
        }

        Hand(int category, List<Integer> card) {
            rank = new ArrayList<>();
            rank.add(category);
            rank.addAll(card);
        }

        @Override
        public int compareTo(Hand other) {
            for (int i = 0; i < Math.min(rank.size(), other.rank.size()); i++) {
                int cmp = rank.get(i).compareTo(other.rank.get(i));
                if (cmp != 0) return cmp;
            }
            return Integer.compare(rank.size(), other.rank.size());
        }
    }
}