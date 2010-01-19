(ns examples.dgraph.simple
  (:require [dgraph :as dg]))


;; This returns:
;; [1 2 3 10 2 12 15]
(let [graph1 (dg/make-dgraph :a 1
                             :b 2
                             :c (dg/lazy #(+ (% :a) (% :b))))
      graph2 (graph1 :a 10 :d 15)]
  [(graph1 :a) (graph1 :b) (graph1 :c)
   (graph2 :a) (graph2 :b) (graph2 :c) (graph2 :d)])


;; This returns:
;; [1 2 3 10 2 12 15
;;  {:inner {:msg "hello world", :many ["one" "two"]}}
;;  {:inner {:msg "hello", :many ["one" "two" "three"]}}]
(let [graph1 (dg/make-dgraph :a 1
                             :b 2
                             :c (dg/lazy #(+ (% :a) (% :b)))
                             :e {:inner {:msg "hello" :many ["one" "two"]}})
      graph2 (graph1 :a 10 :d 15)
      graph3 (dg/assoc-in-node graph2 :e [:inner :msg] "hello world")
      graph4 (dg/update-in-node graph2 :e [:inner :many] conj "three")]
  [(graph1 :a) (graph1 :b) (graph1 :c)
   (graph2 :a) (graph2 :b) (graph2 :c) (graph2 :d)
   (graph3 :e)
   (graph4 :e)])
