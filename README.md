dgraph is a dependency graph library for Clojure.

This code is inspired by Kenny Tilton's Cells project
(http://common-lisp.net/project/cells/).

dgraph aims to offer a (mostly) pure functional data structure whose nodes
behave like cells in a spreadsheet, i.e., they form a dependency graph. In
typical use, computed nodes calculate their values lazily when needed and cache
them. Changing the value of a node does not destructively modify the graph;
instead, it returns a new graph with the node's value changed and the node's
dependent (child) values invalidated.

This data structure has many uses. In particular, it helps eliminate repeated
clutter in UI code where the state of a display element may depend on the state
of several other elements. Consider, for example, an action button which needs
to be activated if and only if several other input elements are filled in and
validate correctly. Normally, each of these elements' callbacks would have to
enable the action button separately, while also checking the state of the other
fields. Although this mess may be abstracted out into a function, each callback
would still have to be sure to call that function. On the other hand, if the
state of each input element and the action button become nodes on a dependency
graph, then the button can just activate itself when the other nodes all achieve
valid state.



## Sample Code

    (require ['dgraph :as 'dg])

    (let [graph1 (dg/make-dgraph :a 1
                                 :b 2
                                 :c (dg/lazy #(+ (% :a) (% :b))))
          graph2 (graph1 :a 10 :d 15)]
      [(graph1 :a) (graph1 :b) (graph1 :c)
       (graph2 :a) (graph2 :b) (graph2 :c) (graph2 :d)])

This returns `[1 2 3 10 2 12 15]`. It first creates a dependency graph (graph1)
with two stored nodes, :a and :b, and a lazily computed node :c which depends on
:a and :b (meaning :c is a child of :a and :b). The value of :c is computed only
when first retrieved, and cached thereafter. It then extends graph1 into graph2
by "changing" the value of :a and adding a new stored node, :d. Note that graph1
remains unmodified. graph1 and graph2 share as much structure as possible, just
like Clojure's built-in persistent data structures.



## Getting Started

dgraph has no dependencies except Clojure itself. dgraph works with both Clojure
1.0 and 1.1. Clojure 1.1 is recommended, however, as bound-fn is required for
computed nodes which execute code on more than one thread (see the Documentation
and Limitations sections below). Clojure 1.1 is also required for the dgraph
test suite.

1. Clone the dgraph Git repository.
2. Run `ant package`. Please note that dgraph is not AOT compiled. (It is a tiny
   library.)
3. Copy the dgraph jar to wherever you normally keep Clojure dependencies. Make
   sure to add the jar to your project's classpath.



## Documentation

dgraph has two major kinds of nodes: stored and computed. Computed nodes may be
lazy, eager, or patient. Computed nodes are marked in the definition of a
dependency graph with invocations to dg/lazy, dg/eager, or dg/patient. These
three functions take one argument: a function of the dependency graph. The
definition of node :c in the example above, therefore, can be written more
verbosely as:

    (dg/lazy
      (fn [dependency-graph]
        (+ (dependency-graph :a)
           (dependency-graph :b))))

The different computed nodes have the following semantics:
 - Lazy nodes evaluate only when retrieved; once computed, they cache their
   values and use the cache on subsequent retrieval. An invalidated lazy node
   evaluates only when subsequently retrieved.
 - Eager nodes evaluate their values immediately upon creation and immediately
   after invalidation. They also cache their values.
 - Patient nodes are a hybrid between lazy and eager. Like lazy nodes, they do
   not evaluate until first retrieved. However, like eager nodes, they
   immediately evaluate after invalidation. They also cache their values.

Eager and patient nodes are most useful when their computed functions have side
effects. Their side effects automatically trigger when a parent node changes or
invalidates.

dgraph uses a dynamic variable internally to trace node dependencies. Therefore,
computed nodes with dependencies accessed in a spawned thread must use bound-fn
or bound-fn* to make sure child threads have access to the dynamic variables
defined in invocations of dg/lazy, dg/patient, or dg/eager.

In addition to setting and retrieving node values using the graph object as a
function (recommended for normal use), dg/getn and dg/setn are provided for
convenience when storing the graph in an atom or ref:

    (let [graph1 (atom (dg/make-dgraph :a 1 :b 2))
          graph2 (ref (dg/make-dgraph :a 1 :b 2))]
      (swap! graph1 dg/setn :a 10)
      (dosync (alter graph2 dg/setn :a 10))
      [(@graph1 :a) (@graph2 :a)])



## Limitations

 - dgraph does not attempt to detect cyclical dependencies between computed
   nodes, so watch out for infinite loops.
 - dgraph does not currently allow adding or modifying computed nodes. This is
   not restricted in the code, but may have unexpected behavior on node
   invalidation.
 - See the note above concerning the use of bound-fn and threads spawned inside
   computed nodes.



## License

dgraph is distributed under the MIT license.
