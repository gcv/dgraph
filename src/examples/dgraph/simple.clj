(ns examples.dgraph.simple
  (:require [dgraph :as dg]))

(let [graph1 (dg/make-dgraph :a 1
                             :b 2
                             :c (dg/lazy #(+ (% :a) (% :b))))
      graph2 (graph1 :a 10 :d 15)]
  [(graph1 :a) (graph1 :b) (graph1 :c)
   (graph2 :a) (graph2 :b) (graph2 :c) (graph2 :d)])
