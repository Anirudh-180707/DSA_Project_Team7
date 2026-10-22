package relief.heap;

/**
 * Optional observer: the heap calls it at each structural step so the visualiser
 * can take a snapshot and animate it. When no listener is attached (benchmark,
 * replay) the heap pays nothing but a null check.
 */
public interface StepListener<T> {

    enum Step {
        INSERT,            // a = new node
        MERGE,             // a = min of combined root list
        REMOVE_MIN,        // a = node removed
        PROMOTE_CHILDREN,  // a = removed min whose children moved to the root list
        LINK,              // a = child that was linked under b
        CONSOLIDATED,      // a = new min
        DECREASE,          // a = node whose key dropped
        CUT,               // a = node cut from parent b
        CASCADING_CUT,     // a = marked ancestor cut from parent b
        MARK,              // a = node that just got marked
        DELETE             // a = node being deleted
    }

    void onStep(FibonacciHeap<T> heap, Step step, FibNode<T> a, FibNode<T> b);
}
