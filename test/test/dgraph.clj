(ns test.dgraph
  (:require [dgraph :as dg])
  (:use clojure.test))


(deftest internal-details
  ;; XXX: This is a slightly incorrect test, in that it looks at the correctness
  ;; of some the data structures representing dgraphs. It helps catch some
  ;; binding bugs.
  (let [graph1 (dg/make-dgraph :a 1 :b 2)
        graph2 (graph1 :c 3 :d 4 :e 5)
        node-name-extractor (fn [graph]
                              (set (map (fn [node-fn] (@(node-fn) :name))
                                        (vals (graph)))))]
    (is (= #{:a :b} (node-name-extractor graph1)))
    (is (= #{:a :b :c :d :e} (node-name-extractor graph2)))))


(deftest stored-only
  (let [graph1 (dg/make-dgraph :a 1 :b 2)
        graph2 (graph1 :c 3 :d 4 :e 5)]
    ;; original graph1
    (is (= (graph1 :a) 1))
    (is (= (graph1 :b) 2))
    (is (thrown-with-msg? RuntimeException #":c not found in dependency graph"
          (graph1 :c) 3))
    ;; new graph2
    (is (= (graph2 :a) 1))
    (is (= (graph2 :b) 2))
    (is (= (graph2 :c) 3))
    (is (= (graph2 :d) 4))
    (is (= (graph2 :e) 5))))


(deftest stored-maps
  (let [graph1 (dg/make-dgraph :m {:inner1 {:inner2 {:a 1 :b [2 3]}}})
        graph2 (dg/update-in-node graph1 :m [:inner1 :inner2 :b] conj 4)
        graph3 (dg/assoc-in-node graph2 :m [:inner1 :inner2 :c] 5)]
    (is (= (graph3 :m) {:inner1 {:inner2 {:a 1 :b [2 3 4] :c 5}}}))))


(deftest computed-lazy
  (let [side-effect (atom 0)
        graph (atom (dg/make-dgraph :a 1
                                    :b 2
                                    :c (dg/lazy #(do (swap! side-effect inc)
                                                     (+ (% :a) (% :b))))))]
    (is (= (@graph :a) 1))
    (is (= (@graph :b) 2))
    (is (= @side-effect 0))
    (is (= (@graph :c) 3))
    (is (= @side-effect 1))
    (is (= (@graph :c) 3))
    (is (= @side-effect 1))
    ;; add a new value
    (swap! graph dg/setn :d 4)
    (is (= (@graph :a) 1))
    (is (= (@graph :b) 2))
    (is (= (@graph :c) 3))
    (is (= (@graph :d) 4))
    (is (= @side-effect 1))
    ;; add a new value which forces :c to invalidate
    (swap! graph dg/setn :a 10)
    (is (= @side-effect 1)) ; not yet
    (is (= (@graph :a) 10))
    (is (= (@graph :b) 2))
    (is (= (@graph :c) 12)) ; now the lazy function defining :c ran
    (is (= @side-effect 2)) ; now it should have updated
    (is (= (@graph :d) 4))))


(deftest computed-lazy-pure-1
  (let [graph1 (dg/make-dgraph :a 1
                               :b 2
                               :c (dg/lazy #(+ (% :a) (% :b))))]
    (is (= (graph1 :c) 3))
    (let [graph2 (graph1 :a 10)]
      (is (= (graph1 :c) 3))
      (is (= (graph2 :c) 12)))))


(deftest computed-lazy-pure-2
  (let [graph1 (dg/make-dgraph :a 1
                               :b 2
                               :c (dg/lazy #(+ (% :a) (% :b))))
        graph2 (graph1 :a 10)]
    (is (= [(graph1 :a)
            (graph1 :b)
            (graph1 :c)
            (graph2 :a)
            (graph2 :b)
            (graph2 :c)]
           [1 2 3 10 2 12]))))


(deftest computed-eager
  (let [side-effect (atom 0)
        graph (atom (dg/make-dgraph :a 1
                                    :b 2
                                    :c (dg/eager #(do (swap! side-effect inc)
                                                      (+ (% :a) (% :b))))))]
    (is (= @side-effect 1))
    (is (= (@graph :a) 1))
    (is (= (@graph :c) 3))
    (is (= @side-effect 1))
    ;; force invalidation; should immediately cause a side effect
    (swap! graph dg/setn :a 10)
    (is (= @side-effect 2))
    (is (= (@graph :c) 12))))


(deftest computed-patient
  (let [side-effect (atom 0)
        graph (atom (dg/make-dgraph :a 1
                                    :b 2
                                    :c (dg/patient #(do (swap! side-effect inc)
                                                        (+ (% :a) (% :b))))))]
    (is (= @side-effect 0))
    (is (= (@graph :a) 1))
    (is (= (@graph :b) 2))
    (is (= (@graph :c) 3))
    (is (= @side-effect 1))
    (swap! graph dg/setn :a 10)
    (is (= @side-effect 2))
    (is (= (@graph :c) 12))))


(deftest transitive-dependencies
  (let [side-effect-mult1 (atom 0)
        side-effect-concat1 (atom 0)
        side-effect-combine1 (atom 0)
        side-effect-mult2 (atom 0)
        side-effect-concat2 (atom 0)
        side-effect-combine2 (atom 0)
        side-effect-mult3 (atom 0)
        side-effect-concat3 (atom 0)
        side-effect-combine3 (atom 0)
        graph-1 (dg/make-dgraph :a 3
                                :b 5
                                :c "hello"
                                :d "world"
                                ;; purely lazy
                                :mult1 (dg/lazy #(do (swap! side-effect-mult1 inc)
                                                     (* (% :a) (% :b))))
                                :concat1 (dg/lazy #(do (swap! side-effect-concat1 inc)
                                                       (format "%s %s"
                                                               (% :c)
                                                               (% :d))))
                                :combine1 (dg/lazy #(do (swap! side-effect-combine1 inc)
                                                        (format "%s %d"
                                                                (% :concat1)
                                                                (% :mult1))))
                                ;; combined lazy and eager
                                :mult2 (dg/lazy #(do (swap! side-effect-mult2 inc)
                                                     (* (% :a) (% :b))))
                                :concat2 (dg/lazy #(do (swap! side-effect-concat2 inc)
                                                       (format "%s %s"
                                                               (% :c)
                                                               (% :d))))
                                :combine2 (dg/eager #(do (swap! side-effect-combine2 inc)
                                                         (format "%s %d"
                                                                 (% :concat2)
                                                                 (% :mult2))))
                                ;; combined lazy and patient
                                :mult3 (dg/lazy #(do (swap! side-effect-mult3 inc)
                                                     (* (% :a) (% :b))))
                                :concat3 (dg/lazy #(do (swap! side-effect-concat3 inc)
                                                       (format "%s %s"
                                                               (% :c)
                                                               (% :d))))
                                :combine3 (dg/patient #(do (swap! side-effect-combine3 inc)
                                                           (format "%s %d"
                                                                   (% :concat3)
                                                                   (% :mult3)))))]
    (is (= @side-effect-combine1 0))
    (is (= @side-effect-concat1 0))
    (is (= @side-effect-mult1 0))
    (is (= @side-effect-combine2 1))
    (is (= @side-effect-concat2 1))
    (is (= @side-effect-mult2 1))
    (is (= @side-effect-combine3 0))
    (is (= @side-effect-concat3 0))
    (is (= @side-effect-mult3 0))
    (is (= (graph-1 :combine1) "hello world 15"))
    (is (= @side-effect-combine1 1))
    (is (= @side-effect-concat1 1))
    (is (= @side-effect-mult1 1))
    (is (= @side-effect-combine2 1))
    (is (= @side-effect-concat2 1))
    (is (= @side-effect-mult2 1))
    (is (= @side-effect-combine3 0))
    (is (= @side-effect-concat3 0))
    (is (= @side-effect-mult3 0))
    (is (= (graph-1 :combine3) "hello world 15"))
    (is (= @side-effect-combine3 1))
    (is (= @side-effect-concat3 1))
    (is (= @side-effect-mult3 1))
    (is (= (graph-1 :mult1) 15))
    (is (= (graph-1 :concat1) "hello world"))
    (is (= @side-effect-combine1 1))
    (is (= @side-effect-concat1 1))
    (is (= @side-effect-mult1 1))
    (is (= @side-effect-combine2 1))
    (is (= @side-effect-concat2 1))
    (is (= @side-effect-mult2 1))
    (is (= @side-effect-combine3 1))
    (is (= @side-effect-concat3 1))
    (is (= @side-effect-mult3 1))
    (is (= (graph-1 :combine3) "hello world 15"))
    (is (= @side-effect-combine3 1))
    (is (= @side-effect-concat3 1))
    (is (= @side-effect-mult3 1))
    ;; check invalidation
    (let [graph-2 (graph-1 :a 7)]
      (is (= @side-effect-combine1 1))
      (is (= @side-effect-concat1 1))
      (is (= @side-effect-mult1 1))
      (is (= @side-effect-combine2 2))
      (is (= @side-effect-concat2 1))
      (is (= @side-effect-mult2 2))
      (is (= @side-effect-combine3 2))
      (is (= @side-effect-concat3 1))
      (is (= @side-effect-mult3 2))
      (is (= (graph-1 :combine1) "hello world 15"))
      (is (= @side-effect-combine1 1))
      (is (= @side-effect-concat1 1))
      (is (= @side-effect-mult1 1))
      (is (= @side-effect-combine2 2))
      (is (= @side-effect-concat2 1))
      (is (= @side-effect-mult2 2))
      (is (= @side-effect-combine3 2))
      (is (= @side-effect-concat3 1))
      (is (= @side-effect-mult3 2))
      (is (= (graph-2 :combine1) "hello world 35"))
      (is (= @side-effect-combine1 2))
      (is (= @side-effect-concat1 1))
      (is (= @side-effect-mult1 2))
      (is (= @side-effect-combine2 2))
      (is (= @side-effect-concat2 1))
      (is (= @side-effect-mult2 2))
      (is (= (graph-2 :combine3) "hello world 35"))
      (is (= @side-effect-combine3 2))
      (is (= @side-effect-concat3 1))
      (is (= @side-effect-mult3 2)))))
