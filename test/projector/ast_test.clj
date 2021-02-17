
(ns projector.ast-test
  (:require [clojure.test :refer :all]
            [projector.ast :refer :all]))


(defn correct-result? [expr correct-result]
  (-> expr
      evaluate
      :result
      (= correct-result)
      is))


(deftest results

  ;; Basic expressions
  (correct-result? '(+ 1 2) 3)

  (is (= 3 (+ 1 2)))

  ;; Evaluate over collections
  (correct-result? '[(+ 1 2) (+ 3 4)] '[3 7])

  ;; Do
  (correct-result? '(do (+ 1 2) (+ 2 3)) 5)

  ;; Let (test parallel binds)
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
  (correct-result? '(reduce +  [1 2 3 4 5 6]) 21))
