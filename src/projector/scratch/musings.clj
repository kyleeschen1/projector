
(ns projector.scratch.musings
  (:require
   [clojure.repl :refer [source]]
   [clojure.walk :as w]))

;;================================================================================
;; Miscellaneous Code (Ignore)

(defn temp [x y z]
  (* x (+ y z)))

(defmacro inject [params fn-body & args]
  (let [s-expr  (w/postwalk-replace (zipmap params args)
                                    fn-body)]
    `(macroexpand-1 '~s-expr)))


;;================================================================================
;; Core Async Practice
;; From https://www.braveclojure.com/core-async/

(require '[clojure.core.async
             :as a
             :refer [>! <! >!! <!! go chan buffer close! thread
                     alts! alts!! timeout]])

(comment

  (def echo-chan (chan 2))
  (go (println (<! echo-chan)))
  (>!! echo-chan "ketchup")
  (>!! echo-chan "mustard")

  (def hi-chan (chan))
  (doseq [n (range 1000)]
    (go (>! hi-chan (str "hi " n))))

  (<!! hi-chan)

  (close! echo-chan)
  (close! hi-chan))


;;================================================================================
;; Dataflow Programming (Practice from Tim Baldridge tutorials)


(defrecord Graph [nodes links])

(defn add-node [graph {:keys [id] :as node}]
  (assoc-in graph [:nodes id] node))

;; "from" and "to" are ports (:out flows into :in)
(defn link [graph a from to b]
  (update-in graph [:links a from] (fnil conj #{}) [to b]))

(defprotocol INode
  (ingest [state port msg]))

;; Keeps things in proper order
(def conj-vec (fnil conj []))

(defn emit [state port msg]
  (update-in state [:outputs port] conj-vec msg))


(defrecord Map [id f]
  INode
  (ingest [state port msg]
    (case port
      :in (emit state :out (f msg)))))

(let [a (atom 0)]
  (defn next-id []
    (swap! a inc)))

(defn map-node [f]
  (->Map (next-id) f))

(def into-vec (fnil into []))
(defn run-node [graph node-id port msgs]
  (let [node (get-in graph [:nodes node-id])
        _ (if-not node
            (throw (ex-info "Node not found"
                            {:node-id node-id})))
        result (reduce
                (fn [node msg]
                  (ingest node port msg))
                node
                msgs)
        
        outputs (:outputs result)
        result (dissoc result :outputs)

        out-data (reduce-kv
                  (fn [acc ports msgs]
                    (let [linked (get-in graph [:links node-id])]

                      (reduce
                       (fn [acc [in-port in-node]]
                         (update-in acc [in-node in-port] into-vec msgs))
                       acc
                       linked)))
                  {}
                  outputs) ]
   [out-data result]))

(-> (->Map :a inc)
    (ingest :in 42 ))

(defn run-graph [graph node-id port msgs]
  (let []))

(-> (->Graph nil nil)
    (add-node (->Map :a inc))
    (add-node (->Map :b #(* % %)))
    (add-node (->Map :c dec))
    (link :a :out :in :b)
    (link :a :out :in :c)
    (run-node :a :in [1 2 3]))

;; Returns a vector with where the outputs should be inserted
;; :a is linked to both :b and :c, which means the set has two elements
;; [{#{[:in :b] [:in :c]} {:out [2 3 4]}} #animator.ast.Map{:id :a, :f #function[clojure.core/inc]}]










;;################################################################################
;; Logic Programming (Practice from Tim Baldridge tutorials... might be useful)

(defn lvar

  ([] (lvar ""))

  ([nm] (gensym (str nm "_"))))

(defn lvar? [v]
  (symbol? v))

(defn walk [s u]
  "Looks for answer in map s,
   and if it fails, returns logic
   variable."
  (if-let [pr (get s u)]
    (if (lvar? pr)
      (recur s pr)
      pr)
    u))

(defn unify [s u v]
  "Returns substitution map
   if conditions can be met, nil
   if otherwise."

  (let [u (walk s u)
        v (walk s v)]

    (cond
      (and (lvar? u)
           (lvar? v)
           (= u v))
      s

      (lvar? u) (assoc s u v)
      (lvar? s) (assoc s v u)

      :else (and (= u v) s) )))


(let [s (lvar "s")
      v (lvar "v")]
  (walk {v 42 s v} s))

(unify {} (lvar "s") (lvar "v"));; {s_13476 v_13477}

(defn == [a b]
  "Returns a function that takes in
   a substitution map. When invoked,
   it returns a vector with the solution
   to the unification of a and b, and
   otherwise an empty set."
  (fn [s]
    (if-let [v (unify s a b)]
      [v]
      [])))

((== 1 2) {}) ;; []
((== (lvar "x") 1) {}) ;; [{x_13469 1}]

(defn -conj
  ([a] a)
  ([a b]
   (fn [s]
     (for [a-ret (a s)
           :when a-ret
           b-ret (b a-ret)
           :when b-ret]
       b-ret)))
  ([a b & more]
   (-conj a (apply -conj b more))))

(let [a (lvar "a")
      b (lvar "b")]
  ((-conj
    (== b a)
    (== a 42)) {})) ;; ({b_13590 a_13589, a_13589 42})

(let [a (lvar "a")
      b (lvar "b")]
  ((-conj
    (== b a)
    (== a 42)
    (== 1 2)) {})) ;; ()

(defn -disj [& goals]
  (fn [s]
    (mapcat (fn [goal]
              (goal s))
            goals)))

(let [a (lvar "a")
      b (lvar "b")]
  ((-disj (-conj
           (== b a)
           (== a 42) 
           (== 1 2))      ;; Now this is the only solution
          (== b 11)) {})) ;; ({b_13746 11})



(defmacro fresh [lvars & body]
  `(let [~@(mapcat (fn [x]
                     `[~x (lvar ~(name x))])
                   lvars)]
     (-conj ~@body)))

((fresh [a b]    ;; allows you to do -conj without let
    (== a 42)
    (== b a)) {});;  ({a_13771 42, b_13772 42})

(defn reify-lvar [results lvars]
  (for [result results]
    (map (partial walk result) lvars)))

(defmacro run [lvars & body]
  `(let [~@(mapcat (fn [x]
                     `[~x (lvar ~(name x))])
                   lvars)
         v# [~@lvars]
         results# ((-conj ~@body) {})]
     (reify-lvar results# v#)))

(run [q]
  (-disj (== q 2)
         (== q 1))) ;; ((2) (1))

(defn conde [& goals]
  (apply -disj
         (map (partial apply -conj) goals)))

;; conde lets you use brackets to indicate conjunction
;; within and disjunction between.


(run [a b]
  (conde
   [(== a 1)
    (== b 2)]
   [(== b 2)])) ;; ((1 2) (a_13883 2))

(run [a b]
  (conde
   [(== a 1)
    (fresh [q]
      (== q a)
      (== b q))]
   [(== b 2)
    (== a b)])) ;; ((1 1) (2 2))

(defn foo [a b]
  (conde
   [(== a 1)]
   [(== b 1)]))

(run [a b]
  (foo a b));;((1 b_13929) (a_13928 1))
;; starts off with two degrees of freedom

(run [a b]
  (foo a b)
  (== a b)) ;; ((1 1))
;; add in another condition, and they mutually constrain
;; each other.

;;==================================================================
;; Lesson 3

(defprotocol ILCons
  (lfirst [this])
  (lnext [this]))

(defn lcons? [x]
  (satisfies? ILCons x))

(extend-type clojure.lang.ISeq
  ILCons
  (lfirst [this]
    (first this))
  (lnext [this]
    (next this)))

(defrecord LCons [h t]
  ILCons
  (lfirst [this]
    h)
  (lnext [this]
    t))


(defn unify [s u v]
  (let [u (walk s u)
        v (walk s v)]
    (cond

      (and (lvar? u)
           (lvar? v)
           (= u v))
      s

      (lvar? u)
      (assoc s u v)

      (lvar? v)
      (assoc s v u)

      (and (lcons? u) (lcons? v))
      (let [s (unify s (lfirst u) (lfirst v))]
        (and s (unify s (lnext u) (lnext v))))

      :else
      (and (= u v) s))))

(defn lcons [h t]
  (->LCons h t))

(defn conso [h t o]
  (== (lcons h t ) o))

(defn firsto [h t]
  (fresh [rest]
    (== (lcons h rest) t)))

(defn resto [r t]
  (fresh [h]
    (== (lcons h r) t)))

(run [q]
  (resto q (range 10)))

(run [q]
  (firsto q (range 10)))

(run [q]
  (conso 1 `() q))
