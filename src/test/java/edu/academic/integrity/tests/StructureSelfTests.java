package edu.academic.integrity.tests;

import edu.academic.integrity.structures.AVLTree;
import edu.academic.integrity.structures.BPlusTree;
import edu.academic.integrity.structures.BTree;
import edu.academic.integrity.structures.BinaryHeap;
import edu.academic.integrity.structures.BinarySearchTree;
import edu.academic.integrity.structures.DisjointSet;
import edu.academic.integrity.structures.DynamicArray;
import edu.academic.integrity.structures.FenwickTree;
import edu.academic.integrity.structures.HashSet;
import edu.academic.integrity.structures.HashTable;
import edu.academic.integrity.structures.LinkedQueue;
import edu.academic.integrity.structures.LinkedStack;
import edu.academic.integrity.structures.SegmentTree;
import edu.academic.integrity.structures.SinglyLinkedList;

/** Standalone invariant and behavior tests requiring no test or collection library. */
public final class StructureSelfTests {
    private int assertions;

    private StructureSelfTests() {
    }

    public static int runAll() {
        StructureSelfTests tests = new StructureSelfTests();
        tests.testLinearStructures();
        tests.testHashStructures();
        tests.testHeap();
        tests.testBinaryTrees();
        tests.testBTree();
        tests.testBPlusTree();
        tests.testRangeTrees();
        tests.testDisjointSet();
        return tests.assertions;
    }

    public static void main(String[] arguments) {
        int assertionsRun = runAll();
        System.out.println("StructureSelfTests passed: " + assertionsRun + " assertions");
    }

    private void testLinearStructures() {
        DynamicArray<Integer> array = new DynamicArray<>(1);
        for (int i = 0; i < 100; i++) array.add(i);
        array.add(50, -1);
        check(array.size() == 101 && array.get(50) == -1, "dynamic-array insertion");
        check(array.removeAt(50) == -1 && array.get(50) == 50, "dynamic-array removal");
        check(array.contains(99) && !array.contains(100), "dynamic-array search");

        SinglyLinkedList<String> list = new SinglyLinkedList<>();
        list.addLast("b");
        list.addFirst("a");
        list.addLast("d");
        list.add(2, "c");
        check(list.size() == 4 && "c".equals(list.get(2)), "linked-list insertion");
        check("a".equals(list.removeFirst()) && "d".equals(list.removeLast()), "linked-list ends");
        check(list.remove("b") && "c".equals(list.getFirst()), "linked-list value removal");

        LinkedStack<Integer> stack = new LinkedStack<>();
        LinkedQueue<Integer> queue = new LinkedQueue<>();
        for (int i = 0; i < 20; i++) {
            stack.push(i);
            queue.offer(i);
        }
        for (int i = 0; i < 20; i++) {
            check(stack.pop() == 19 - i, "stack ordering");
            check(queue.poll() == i, "queue ordering");
        }
    }

    private void testHashStructures() {
        HashTable<CollisionKey, Integer> table = new HashTable<>(1);
        for (int i = 0; i < 300; i++) table.put(new CollisionKey(i), i * 7);
        check(table.size() == 300 && table.validateInvariant(), "hash collision insert/resize");
        for (int i = 0; i < 300; i++) {
            check(table.get(new CollisionKey(i)) == i * 7, "hash lookup");
        }
        for (int i = 0; i < 200; i += 2) {
            check(table.remove(new CollisionKey(i)) == i * 7, "hash removal");
        }
        check(table.size() == 200 && table.validateInvariant(), "hash post-removal invariant");
        table.put(null, 91);
        check(table.get(null) == 91, "hash null key");

        HashSet<CollisionKey> set = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            check(set.add(new CollisionKey(i)), "set first add");
            check(!set.add(new CollisionKey(i)), "set duplicate add");
        }
        check(set.size() == 100 && set.validateInvariant(), "set invariant");
    }

    private void testHeap() {
        BinaryHeap<Integer> minimum = new BinaryHeap<>(true);
        BinaryHeap<Integer> maximum = new BinaryHeap<>(false);
        int[] permutation = permutation(500, 0x51A7);
        for (int value : permutation) {
            minimum.add(value);
            maximum.add(value);
            check(minimum.validateInvariant() && maximum.validateInvariant(), "heap insertion invariant");
        }
        for (int i = 0; i < permutation.length; i++) {
            check(minimum.poll() == i, "min-heap order");
            check(maximum.poll() == permutation.length - 1 - i, "max-heap order");
        }
    }

    private void testBinaryTrees() {
        int[] permutation = permutation(400, 0xA11);
        BinarySearchTree<Integer> bst = new BinarySearchTree<>();
        AVLTree<Integer> avl = new AVLTree<>();
        for (int value : permutation) {
            check(bst.insert(value), "BST insertion");
            check(avl.insert(value), "AVL insertion");
            check(avl.validateInvariant(), "AVL insertion invariant");
        }
        check(bst.validateInvariant() && bst.size() == 400, "BST invariant");
        check(avl.height() < 20, "AVL logarithmic height");
        for (int value : permutation) {
            check(bst.remove(value), "BST deletion");
            check(avl.remove(value), "AVL deletion");
            check(bst.validateInvariant() && avl.validateInvariant(), "tree deletion invariant");
        }
        check(bst.isEmpty() && avl.isEmpty(), "trees empty after deletes");

        int[][] rotationCases = {{30, 20, 10}, {10, 20, 30}, {30, 10, 20}, {10, 30, 20}};
        for (int[] values : rotationCases) {
            AVLTree<Integer> rotationTree = new AVLTree<>();
            for (int value : values) rotationTree.insert(value);
            check(rotationTree.validateInvariant() && rotationTree.height() == 2,
                    "AVL LL/RR/LR/RL rotation");
        }
    }

    private void testBTree() {
        for (int degree = 2; degree <= 5; degree++) {
            BTree<Integer> tree = new BTree<>(degree);
            int[] values = permutation(350, 0xB700 + degree);
            for (int value : values) {
                check(tree.insert(value), "B-tree insertion");
                check(tree.validateInvariant(), "B-tree insertion invariant");
            }
            for (int i = 0; i < values.length; i++) check(tree.contains(i), "B-tree search");
            for (int value : values) {
                check(tree.remove(value), "B-tree deletion");
                check(tree.validateInvariant(), "B-tree deletion invariant");
            }
            check(tree.isEmpty(), "B-tree empty after deletes");
        }
    }

    private void testBPlusTree() {
        for (int order = 3; order <= 6; order++) {
            BPlusTree<Integer, String> tree = new BPlusTree<>(order);
            int[] values = permutation(180, 0xB900 + order);
            for (int value : values) {
                check(tree.insert(value, "v" + value), "B+ insertion");
                check(tree.validateInvariant(), "B+ insertion invariant");
            }
            DynamicArray<BPlusTree.Entry<Integer, String>> range = tree.range(37, 84);
            check(range.size() == 48, "B+ range count");
            for (int i = 0; i < range.size(); i++) {
                check(range.get(i).key() == i + 37, "B+ linked-leaf order");
            }
            for (int i = 0; i < values.length; i++) {
                check(("v" + i).equals(tree.get(i)), "B+ search");
            }
            for (int i = 0; i < 60; i++) {
                check(("v" + values[i]).equals(tree.remove(values[i])), "B+ delete");
                check(tree.validateInvariant(), "B+ post-delete invariant");
            }
            check(tree.size() == 120, "B+ deletion size");
        }
    }

    private void testRangeTrees() {
        long[] values = new long[128];
        for (int i = 0; i < values.length; i++) values[i] = (i * 37L) % 101 - 50;
        SegmentTree segment = new SegmentTree(values);
        for (int round = 0; round < 80; round++) {
            int left = (round * 17) % values.length;
            int right = left + ((round * 23) % (values.length - left));
            long delta = round - 39;
            segment.rangeAdd(left, right, delta);
            for (int i = left; i <= right; i++) values[i] += delta;
            int queryLeft = (round * 11) % values.length;
            int queryRight = queryLeft + ((round * 7) % (values.length - queryLeft));
            long sum = 0;
            long minimum = Long.MAX_VALUE;
            long maximum = Long.MIN_VALUE;
            for (int i = queryLeft; i <= queryRight; i++) {
                sum += values[i];
                minimum = Math.min(minimum, values[i]);
                maximum = Math.max(maximum, values[i]);
            }
            check(segment.querySum(queryLeft, queryRight) == sum, "segment sum");
            check(segment.queryMin(queryLeft, queryRight) == minimum, "segment min");
            check(segment.queryMax(queryLeft, queryRight) == maximum, "segment max");
            check(segment.validateInvariant(), "segment invariant");
        }

        FenwickTree fenwick = new FenwickTree(values);
        check(fenwick.validateInvariant(), "Fenwick construction");
        for (int i = 0; i < values.length; i++) {
            fenwick.add(i, i - 20);
            values[i] += i - 20;
        }
        long running = 0;
        for (int i = 0; i < values.length; i++) {
            running += values[i];
            check(fenwick.prefixSum(i) == running, "Fenwick prefix");
        }
        check(fenwick.validateInvariant(), "Fenwick update invariant");
    }

    private void testDisjointSet() {
        DisjointSet set = new DisjointSet(100);
        for (int i = 1; i < 50; i++) set.union(0, i);
        for (int i = 51; i < 100; i++) set.union(50, i);
        check(set.componentCount() == 2, "disjoint-set component count");
        check(set.componentSize(4) == 50 && set.componentSize(90) == 50,
                "disjoint-set sizes");
        check(!set.connected(0, 50) && set.union(0, 50) && set.connected(0, 50),
                "disjoint-set union");
        check(set.componentCount() == 1 && set.validateInvariant(), "disjoint-set invariant");
    }

    private int[] permutation(int size, int seed) {
        int[] values = new int[size];
        for (int i = 0; i < size; i++) values[i] = i;
        long state = seed & 0xffffffffL;
        for (int i = size - 1; i > 0; i--) {
            state = (state * 1664525L + 1013904223L) & 0xffffffffL;
            int other = (int) (state % (i + 1));
            int temporary = values[i];
            values[i] = values[other];
            values[other] = temporary;
        }
        return values;
    }

    private void check(boolean condition, String description) {
        assertions++;
        if (!condition) throw new AssertionError(description + " (assertion " + assertions + ")");
    }

    private static final class CollisionKey {
        private final int value;
        private CollisionKey(int value) { this.value = value; }
        @Override public int hashCode() { return value & 3; }
        @Override public boolean equals(Object other) {
            return other instanceof CollisionKey && ((CollisionKey) other).value == value;
        }
    }
}
