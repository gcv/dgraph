(ns examples.dgraph.swing
  (:require [dgraph :as dg])
  (:import java.awt.GridLayout
           [javax.swing JFrame JLabel JPanel JTextField]
           javax.swing.event.DocumentListener))


;;; This example tries to show where eager and patient node evaluation comes in
;;; handy. It is, therefore, fairly imperative in nature and even stores its
;;; state dgraph in an atom.
;;;
;;; The example shows three text fields and validates their input. For the
;;; purposes of this example, valid input means that the first field contains
;;; the string "one", the second field contains the string "two", and the third
;;; field contains the string "three". When all three fields validate, two
;;; status lines update to show that they have validated correctly.
;;;
;;; The advantage of using a dgraph in this example is in the simplicity of the
;;; callback which fires when the user edits the input text fields. The callback
;;; just updates a node on the graph with the new input string (:line-1-text,
;;; :line-2-text, or :line-3-text). When one of these nodes updates, the eager
;;; and patient nodes (called :eager! and :patient!) invalidate and trigger
;;; updates to the output labels. Note that the callback (defined in
;;; make-document-listener) does not know anything about any fields other than
;;; the one to which it is attached.
;;;
;;; The eager and patient node distinction in this example is admittedly
;;; contrived. The patient node does not have a value until the user starts
;;; typing, whereas the eager node immediately puts a value in its output label.


(declare make-computed-node)


;;; This makes the dependency graph. Nodes ending in ! are used just for side
;;; effects. Because they are almost the same, they are defined in a separate
;;; make-computed-node function.
(defn make-demo-state [label-eager label-patient]
  (atom (dg/make-dgraph
         :line-1-text nil
         :line-2-text nil
         :line-3-text nil
         :eager! (dg/eager (make-computed-node label-eager "eager"))
         :patient! (dg/patient (make-computed-node label-patient "patient")))))


;;; This builds a computed node to be used primarily for its side effects of
;;; updating the UI. A computed node must be a function of the dgraph.
(defn make-computed-node [output-label node-type-string]
  #(if (and (= (% :line-1-text) "one")
            (= (% :line-2-text) "two")
            (= (% :line-3-text) "three"))
       (.setText output-label (format "(%s node) valid" node-type-string))
       (.setText output-label (format "(%s node) not valid" node-type-string))))


;;; This is mostly Swing boilerplate, but note the simplicity of the callback
;;; function.
(defn make-document-listener [state field text-node-name]
  (let [callback (fn [] (swap! state dg/setn text-node-name (.getText field)))]
    (.addDocumentListener (.getDocument field)
                          (proxy [DocumentListener] []
                            (changedUpdate [_] (callback))
                            (insertUpdate [_] (callback))
                            (removeUpdate [_] (callback))))))


;;; Main entry point for the example. Mostly Swing UI boilerplate code.
(defn demo-app []
  (let [layout (GridLayout. 0 1)
        line-1 (JTextField.)
        line-2 (JTextField.)
        line-3 (JTextField.)
        label-eager (JLabel.)
        label-patient (JLabel.)
        panel (doto (JPanel.)
                (.setOpaque true)
                (.setLayout layout)
                (.add line-1)
                (.add line-2)
                (.add line-3)
                (.add label-eager)
                (.add label-patient))
        ;; This is where the dependency graph is created:
        state (make-demo-state label-eager label-patient)]
    ;; Attach callbacks to the text fields:
    (make-document-listener state line-1 :line-1-text)
    (make-document-listener state line-2 :line-2-text)
    (make-document-listener state line-3 :line-3-text)
    ;; Done; just show the window.
    (doto (JFrame. "dgraph demo")
      (.setContentPane panel)
      (.setSize 200 150)
      (.setVisible true))))
