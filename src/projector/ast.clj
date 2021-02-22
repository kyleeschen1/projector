

(ns projector.ast
  (:require
   [clojure.repl :refer [source dir]]
   [rewrite-clj.node :as n]
   [rewrite-clj.zip :as z]))

;;################################################################################
;; To-Do:
;; ---------------------
;; Known Bugs
;;
;; - Let / Do immeadiately evaluate, leaving no intermediate states in script
;;
;; - Anonymous functions appear in reader form in scripts
;;
;; - When displaying values for vars in closures, I  miss values that are assigned
;;   at closure creation because I subset the bindings on parameter names.
;;
;; - Importing only works for non-variadic functions.
;;
;; - Evaluator errors when direct macro calls are made during the animation. I believe
;;   that this is because our symbol resolution works differently for macros that most vars.
;;
;; Add support for:
;;  - macros
;;  - cond (specifically)
;;  - loop
;;  - recur
;;  - def / defn
;;  - maps (assoc, dissoc, etc.)
;;  - reference types
;;  - multi-arity functions
;;
;; Add indexing for expressions that don't contain it
;; Add testing for indexing
;; Add function level and expansion level indices



;;################################################################################
;; Evaluation
;;
;; Credit to Tim Baldridge's self-interpreter as a jumping off point for -eval
;; https://github.com/halgari/clojure-tutorial-source/blob/master/src/interpreter/interpreter.clj


(declare -eval)
(declare -eval-over-coll)
(declare -eval-fn-body)
(declare form->type)
(declare update-locals)
(declare sequential-bind)
(declare log-eval)
(declare label-fn-as-expansion)
(declare can-have-meta?)

(def special-forms '#{quote fn if let do})

(defn special-form? [form]
  (and (coll? form)
       (special-forms (first form))))

(defn sf->key [form]
  (-> form
      first
      special-forms
      keyword))

(defn self-resolving? [form]
  (or
   (list? form)
   (integer? form)
   (keyword? form)))

(defn collection? [form]
  (or
   (map? form)
   (set? form)))

(defn invokable? [form locals]
  (or
   (:eval? (meta form)) 
   (and (coll? form)
        (or  (fn? (first form))
             (contains? locals (first form)) ;; Must revise when non-functions are added to environment
             (fn? (eval (first form)))))))

(defn form->type [probe locals form]
  (cond
    (special-form? form)      (sf->key form)
    (invokable? form locals)  :invokable
    (self-resolving? form)    :self-resolving
    (symbol? form)            :symbol
    (vector? form)            :vector
    :else                     :other))


(def -eval nil) ;; Reset evaluation multimethod
(defmulti -eval form->type)

(defmethod -eval :self-resolving
  [probe locals form]
  form)

(defmethod -eval :other
  [probe locals form]
  form)

(defmethod -eval :vector
  [probe locals form]
  (->> form
       (-eval-over-coll probe locals)
       (vec)))

(defmethod -eval :symbol
  [probe locals form]
  (let [v (get locals form ::fail)]
    (if (identical? v ::fail)
      (resolve form)
      (log-eval probe locals v))))

(defmethod -eval :invokable
  [probe locals form]
  (let [[f & args] form
        f (log-eval probe locals f)
        f (label-fn-as-expansion f form)
        args (-eval-over-coll probe locals args)]
    (apply f args)))

(defmethod -eval :quote
  [probe locals [sym & args]]
  (first args))

(defmethod -eval :fn
  [probe locals [_ & [params fn-body]]]
  (let [f (fn [into-index]
            (fn [& args]
              (-eval-fn-body into-index
                             probe
                             locals
                             fn-body
                             params
                             args)))]
    (with-meta f  {:expansion true
                   :fn-body fn-body
                   :closure? (instance? clojure.lang.AFunction fn-body)
                   :mappings locals})))

(defmethod -eval :if
  [probe locals [_ & args]]
  (let [[test then else] args]
    (if (log-eval probe locals test)
      (log-eval probe locals then true)
      (log-eval probe locals else true))))

(defmethod -eval :do
  [probe locals [_ & args]]
  (->> args
       (-eval-over-coll probe locals)
       last))

(defmethod -eval :let
  [probe locals [_ & [bindings & body]]]
  (let [new-locals (sequential-bind probe locals bindings)
       implicit-do (cons 'do body) ]
    (log-eval probe new-locals implicit-do)))

;;================================================================================
;; Helpers

(defn label-fn-as-expansion [f form]
  (if (:expansion (meta f))
    (f (:index (meta form)))
    f))

(defn update-locals
  [locals params args]
  (->> args
       (zipmap params)
       (merge locals)))

(defn sequential-bind
  [probe locals bindings]
  (reduce (fn [old-env [k v]]
            (merge old-env
                   {k (log-eval probe old-env v)}))
          locals
          (partition 2 bindings)))


(defn -eval-over-coll [probe locals form]
  (map (partial log-eval probe locals) form))

(defn -eval-fn-body [into-index probe locals fn-body params args]

  ((:enter-fn-body probe) into-index locals args params fn-body)

  (let [new-locals (update-locals locals params args)
        result     (log-eval probe new-locals fn-body)]

    ((:exit-fn-body probe) into-index new-locals fn-body)

    result))


;;================================================================================
;; Indexing

(require '[clojure.zip :as zip])
(require '[clojure.walk :as w])

(defn initialize-indexer []

  (let [counter (atom 0)]

    (fn [node]

      (swap! counter inc)

      (if (not (or (list? node)
                   (vector? node))) 
        node
        (vary-meta node
                   merge
                   {:index @counter
                    :display node
                    :eval? true})))))

(defn index-fn-body [fn-body]
  (w/postwalk (initialize-indexer) fn-body))

(defn loc->zip [loc]
  "Turns a loc at the end back into a zipper
   in a way that preserves meta-data."
  (let [make-node (fn [node children]
                    (if (meta node)
                      (with-meta children (meta node))
                      children))]
    (zip/zipper seq? seq  make-node loc)))

(defn at-index? [loc index]
  (= index (-> loc
               zip/node
               meta
               :index)))

(defn move-loc-to-index [loc index]
  "Moves a loc to a specific index."

  (cond

    (zip/end? loc)
    (loc->zip (zip/root loc))

    (zip/end? (zip/next loc))  ;; Possible: What happens if you want to access last element?
    (loop [loc loc]

      (cond

        (at-index? loc index)
        loc

        (zip/up loc)
        (recur (zip/prev loc))

        :else loc ))

    (at-index? loc index)
    loc

    :else (recur (zip/next loc) index)))


(defn edit-loc-at-index [loc index f]
  (-> loc
      (move-loc-to-index index)
      (zip/edit f)))

;;================================================================================
;; Logging


(defn log-eval

  ([probe locals form]
   (log-eval probe locals form false))

  ([probe locals form tail-call]
   (do
     ((:indicate-tail-call probe) tail-call form)
     ((:return-from-state  probe)
      locals
      form
      tail-call
      (-eval probe locals form)))))

;; Gen probe returns a map that keys closures that append
;; the script with different kinds of evaluation data as it
;; zips around the interpreter.
;;
;; (To-Do: Have every function end with "(gen-probe script)"
;; so that we can eliminate the script atom. Currently, this
;; creates issues with the function :return-from-state, which
;; needs to return a result and not another probe closure.)

(defn gen-probe [script]

  {:return-from-state (fn [locals form tail-call result]
                        (when-let [index (:index (meta form))]
                          (swap! script conj {:index index
                                              :instr-type :return-from-state
                                              :form form
                                              :tail-call-return tail-call
                                              :result (if (instance? clojure.lang.AFunction result) 
                                                        form
                                                        result)}))
                        result)

   :enter-fn-body (fn [into-index locals args params fn-body]
                    (swap! script conj {:instr-type  :enter-fn-body
                                        :mappings (zipmap params args) ;; For closures, must include local state ;;(update-locals locals params args)
                                        :form fn-body
                                        :into-index into-index}))

   
   :exit-fn-body (fn [into-index locals fn-body]
                   (swap! script conj {:instr-type :exit-fn-body}))

   :indicate-tail-call  (fn [tail-call? form]
                          (when tail-call?
                            (swap! script conj {:instr-type :tail-call
                                                :index (:index (meta form))})))


   })


;;================================================================================
;; Frame Generation

(declare inject-vals-into-form)
(declare add-frame)
(declare add-to-stack)
(declare bind-nested-exprs)
(declare set-root-at-index)
(declare produce-focus-frames)

(def script->frames nil)
(defmulti script->frames (fn [[instr & script] _ _ _]
                           (:instr-type instr)))


(defmethod script->frames :start-script
  [[{:keys [form]} & script] _ frames  stack]
  (script->frames script
                  (index-fn-body form)
                  (conj frames form)
                  stack))

(defmethod script->frames :end-script
  [_ _ frames _]
  (dedupe frames))

(defmethod script->frames :enter-fn-body
  [[{:keys [form into-index mappings]} & script] old-loc frames stack]
  (let [loc-w-vals  (->> form
                         (inject-vals-into-form mappings)
                         (loc->zip))
        new-stack (add-to-stack stack
                                old-loc
                                into-index)]
    (script->frames script
                    loc-w-vals
                    (add-frame frames loc-w-vals new-stack)
                    new-stack)))

(defn gen-focus-frames [loc index result]
  (let [focus (edit-loc-at-index loc
                                 index
                                 (fn [node] [:> node :>]))
        transform (zip/edit focus (fn [_] [:> result :>]))
        unfocus (zip/edit transform (fn [_] result))] 
    [focus transform unfocus]))

(defmethod script->frames :return-from-state-with-focus
  [[{:keys [index result]} & script] loc frames stack]
  (let [focused-frames (gen-focus-frames loc
                                         index
                                         result)] 
    (script->frames script
                    (last focused-frames)
                    (-> frames
                        (add-frame (first focused-frames) stack)
                        (add-frame (second focused-frames) stack)
                        (add-frame (last focused-frames)  stack))
                    stack)))


(defmethod script->frames :return-from-state
  [[{:keys [index result]} & script] loc frames stack]
  (let [new-loc (edit-loc-at-index loc index (fn [_] result))] 
    (script->frames script
                    new-loc
                    (add-frame frames new-loc stack)
                    stack)))


(defmethod script->frames :tail-call
  [[{:keys [index]} & script] loc frames stack]
  (if index
    (let [new-loc (set-root-at-index loc index)
          new-frames (add-frame frames new-loc stack)]
      (script->frames script
                      new-loc
                      new-frames
                      stack))
    (script->frames script
                    loc
                    frames
                    stack)))

(defmethod script->frames :exit-fn-body
  [script _ frames stack]
  (script->frames (next script)
                  (peek stack)
                  frames
                  (pop stack)))


;; ================================================================================
;; Frame Generation Helpers

(declare strip-var-of-ns)
(declare can-have-meta?)
(declare mapply)

(defn inject-vals-into-form [mappings form]
  (mapply form
          (fn [form]
            (cond
              
              (list? form)
              (map (partial inject-vals-into-form mappings) form)
              
              :else
              (if-let [val (get mappings form nil)]
                (strip-var-of-ns val)
                (strip-var-of-ns form))))))



(defn add-frame [frames loc stack]
  (->> stack
       rest
       (bind-nested-exprs loc)
       (conj frames)))

(defn add-to-stack [stack loc into-index]
  (conj stack (if into-index
                (move-loc-to-index loc into-index)
                loc)))

(defn bind-nested-exprs [loc stack]
  (let [inject (fn [new old]
                 (-> old
                     (zip/replace new)
                     (zip/root)))]
    (reduce inject
            (zip/root loc)
            (reverse stack)))) ;; <- an evil that comes from my inability to build the stack in an order that doesn't evaluate quoted forms


(defn strip-var-of-ns [v]
  (mapply v (fn [x]
              (if (var? x)
                (-> x symbol name symbol)
                x))))



(defn can-have-meta? [x]
  (instance? clojure.lang.IMeta x))

(defn mapply [x f]
  (let [result (f x)]
    (if (and (can-have-meta? result)
             (meta x))
      (with-meta result (meta x))
      result)))

(defn set-root-at-index [loc index]
  (-> loc
      (move-loc-to-index index)
      zip/node
      (vector nil)
      (with-meta (meta loc))))

;;================================================================================
;; Test Animations


(defn evaluate [quoted-form env]
  (let [script (atom [{:instr-type :start-script
                       :form quoted-form}])
        probe  (gen-probe script)
        result (log-eval probe env quoted-form)]
    {:result result
     :script (conj @script {:instr-type :end-script})}))

(defmacro expr->script [expr env]
  `(-> '~expr
       (evaluate ~env)
       :script))

(defmacro expr->frames [expr env]
  `(script->frames (expr->script ~expr ~env)
                   '~expr
                   []
                   []))


(comment

  ;; Some quick spot-checks that will get formalized
  ;; into tests once I lock down the script format.
  
  ;; These work fine
  (def script-factorial-rec (expr->script (factorial-rec 5) env))

  (def film-rec (expr->frames (factorial-rec 5) env))
  (def film-acc  (expr->frames (factorial-acc 5 1) env))
  (def film-filter (expr->frames (filter odd? '(1 2 3 4 5 6)) env))
  
  ;; Doesn't yet work on deeply nested lambdas (reader form and closure env substitution)
  (def film-fact-k (expr->frames (factorial-cont 5 identity) env))
  (def film-y (expr->frames (factorial-y 5) env))

   ;; Immeadiately evaluates, leaving only initial and end frames  (some issue with the implicit do)
  (def film-let (expr->script (let [x 1 y 1] (+ x 1)) env)) )

;;================================================================================
;; Generating an Environment from Text File

(require '[clojure.spec.alpha :as spec])
(require '[clojure.core.specs.alpha :as specs])


(defn  destructure-defns [form]
  (let [destructured  (spec/conform ::specs/defn-args (rest form))]
    (when (map? destructured)
      (let [name (:name destructured)
            args (get-in destructured [:bs 1 :args :args])
            args (mapv second args)
            body (-> destructured
                     (get-in [:bs 1 :body 1 0])
                     (index-fn-body))]
        {name (list 'fn args body)}))))

(defn gen-env-from-file [file]
  (->> file
       slurp
       (#(str "[" % "]"))
       read-string
       (map destructure-defns)
       (filter (comp not nil?))
       (into {})))


;;================================================================================
;; Tests


(require '[clojure.test :as t])

(defn gen-result-checker [env]
  (fn [expr correct-result]
    (-> expr
        (evaluate env)
        :result
        (= correct-result)
        t/is)))


(t/deftest results

  (let [correct-result?  (-> "animations/sample.clj"
                             gen-env-from-file
                             gen-result-checker)]

    ;; Basic expressions
    (correct-result? '(+ 1 2) 3)

    ;; Evaluate over collections
    (correct-result? '[(+ 1 2) (+ 3 4)] '[3 7])

    ;; Do
    (correct-result? '(do (+ 1 2) (+ 2 3)) 5)

    ;; Let (test sequential binds) This looks like it's going straight to eval
    (correct-result? '(let [x 1 y 3] (+ x y)) 4)
    (correct-result? '(let [x 1 y x z y] (+ x y z)) 3)
    (correct-result? '(let [x 3 y (factorial-rec x)] (+ x y)) 9)

    ;; Recursive Functionss
    (correct-result? '(count [1 2 3 4 5 6]) 6)
    (correct-result? '(factorial-rec 5) 120)
    (correct-result? '(factorial-acc 5 1) 120)
    (correct-result? '(factorial-y 5) 120)
    (correct-result? '(factorial-cont 5 identity) 120)


    ;; Higher Order Functions
    (correct-result? '(map inc '(1 2 3 4 5 6)) '(2 3 4 5 6 7))
    (correct-result? '(map inc  [1 2 3 4 5 6]) '(2 3 4 5 6 7))

    (correct-result? '(filter odd? '(1 2 3 4 5 6)) '(1 3 5))
    (correct-result? '(filter odd?  [1 2 3 4 5 6]) '(1 3 5))

    (correct-result? '(reduce + '(1 2 3 4 5 6)) 21)
    (correct-result? '(reduce +  [1 2 3 4 5 6]) 21))) 

(when true
  (t/run-tests))





