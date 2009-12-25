(ns dgraph)



;;; ----------------------------------------------------------------------------
;;; types and dynamic variables
;;; ----------------------------------------------------------------------------


(defstruct node :name :kind :quine :cache-valid? :cache :children)


(def #^{:private true} *node-name* nil)
(def *get-node-caller* nil)



;;; ----------------------------------------------------------------------------
;;; node types
;;; ----------------------------------------------------------------------------

(defn stored [stored-val]
  (let [node-name *node-name*
        node-state (atom (struct-map node :name node-name
                                          :kind :stored
                                          :quine (delay (binding [*node-name* node-name]
                                                          (stored stored-val)))
                                          :children #{}))
        node-fn (fn node-fn
                  ([_] stored-val)
                  ([] node-state))]
    node-fn))


(defn- computed [kind val-fn]
  (let [node-name *node-name*
        node-state (atom (struct-map node :name node-name
                                          :kind kind
                                          :quine (delay (binding [*node-name* node-name]
                                                          (computed kind val-fn)))
                                          :cache-valid? false
                                          :children #{}))
        node-fn (fn node-fn
                  ([dgraph]
                     (let [nodes (dgraph)]
                       (binding [*get-node-caller* node-name]
                         (if (@node-state :cache-valid?)
                             (@node-state :cache)
                             (let [new-value (val-fn dgraph)]
                               (swap! node-state assoc :cache new-value :cache-valid? true)
                               new-value)))))
                  ([] node-state))]
    node-fn))


(defn lazy [val-fn]
  (computed :lazy val-fn))


(defn eager [val-fn]
  (computed :eager val-fn))


(defn patient [val-fn]
  (computed :patient val-fn))



;;; ----------------------------------------------------------------------------
;;; node and dependency graph construction
;;; ----------------------------------------------------------------------------

(defn- computed-node-expr? [expr]
  (and (list? expr)
       (let [f (first expr)
             f-str (str f)
             actual (.substring f-str (inc (.lastIndexOf f-str "/")))]
         (or (= actual "lazy") (= actual "eager") (= actual "patient")))))


(defn- one-node* [node-name node-value]
  {node-name `(binding [dgraph/*node-name* ~node-name]
                ~(if (computed-node-expr? node-value)
                     node-value
                     `(stored ~node-value)))})


(defmacro #^{:private true} one-node [node-name node-value]
  (one-node* node-name node-value))


(defn- uncached-nodes [nodes]
  (remove nil?
          (map (fn [[node-name node-fn]]
                 (let [node-state (node-fn)]
                   (when-not (or (= (@node-state :kind) :stored)
                                 (@node-state :cache-valid?))
                     node-name)))
               nodes)))


(defn- invalidate [nodes node-name]
  (let [node-fn (nodes node-name)
        node-state (node-fn)
        node-quine (@node-state :quine)
        node-kids (@node-state :children)]
    (reduce merge
            {node-name (force node-quine)}
            (map #(invalidate nodes %) node-kids))))


(defn evaluate-nodes! [dgraph node-kinds-set]
  (let [nodes (dgraph)]
    (doseq [[node-name node-fn] nodes]
      (let [node-state (node-fn)
            node-kind (@node-state :kind)]
        (when (contains? node-kinds-set node-kind)
          (node-fn dgraph))))))



;;; ----------------------------------------------------------------------------
;;; node manipulation
;;; ----------------------------------------------------------------------------

(declare make-dgraph*)


(defn- get-node [dgraph node-name]
  (let [nodes (dgraph)]
    (if-let [node-fn (nodes node-name)]
      (let [node-state (node-fn)]
        (when-not (nil? *get-node-caller*)
          (swap! node-state assoc :children (conj (@node-state :children) *get-node-caller*)))
        (node-fn dgraph))
      ;; no node-fn means the node is not in the graph
      (throw (RuntimeException. (format "%s not found in dependency graph" node-name))))))


(defn- set-node [dgraph & args]
  (let [nodes (dgraph)
        new-node (fn [[node-name node-value]]
                   (merge (one-node node-name node-value)
                          ;; Invalidate dependencies, if any. Also make copies
                          ;; of all computed nodes which have not yet been
                          ;; evaluated; it is unsafe for them to share state
                          ;; with the new graph.
                          (when-let [node-fn (nodes node-name)]
                            (let [node-state (node-fn)
                                  node-kids (@node-state :children)
                                  uncached (uncached-nodes nodes)
                                  new-kids (reduce merge
                                                   {}
                                                   (map #(invalidate nodes %)
                                                        (concat node-kids uncached)))]
                              new-kids))))
        new-graph (make-dgraph* (apply merge nodes (map new-node (partition 2 args))))]
    (evaluate-nodes! new-graph #{:eager :patient})
    new-graph))


(defn getn [dgraph & args]
  (apply dgraph args))


(defn setn [dgraph & args]
  (apply dgraph args))



;;; ----------------------------------------------------------------------------
;;; constructor, accessor, setter
;;; ----------------------------------------------------------------------------

(defn make-dgraph* [nodes]
  (let [dgraph (fn dgraph
                 ([] nodes)
                 ([node-name] (get-node dgraph node-name))
                 ([node-name new-val & other-args]
                    (apply set-node dgraph node-name new-val other-args)))]
    dgraph))


(defmacro make-dgraph [& args-raw]
  (let [args-split (partition 2 args-raw)
        args-cooked (map (fn [[node-name node-value]]
                           (one-node* node-name node-value))
                         args-split)
        nodes (apply merge args-cooked)]
    `(let [nodes# ~nodes
           dgraph# (make-dgraph* nodes#)]
       (evaluate-nodes! dgraph# #{:eager})
       dgraph#)))
