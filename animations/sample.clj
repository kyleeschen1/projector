

(defn count [coll]
  (if (empty? coll)
    0
    (inc (count (next coll)))))

(defn Y [f]
  ((fn [x]
     (x x))
   (fn [x]
     (f (fn [y]
          ((x x) y))))))

(defn factorial-rec [n]
  (if (zero? n)
    1
    (* n (factorial-rec (dec n)))))

(defn factorial-acc [n acc]
  (if (zero? n)
    acc
    (factorial-acc (dec n) (* n acc))))


(defn factorial-y [n]
  ((Y
    (fn [continuation]
      (fn [n]
        (if (zero? n)
          1
          (* n (continuation (dec n)))))) ) n))

(defn factorial-cont [n k]
  (if (zero? n)
    (k 1)
    (factorial-cont (dec n)
                    (fn [x]
                      (k (* n x))))))

(defn map [f coll]
  (if (empty? coll)
    coll
    (cons (f (first coll))
          (map f (rest coll)))))

(defn filter [pred coll]
  (if (empty? coll)
    coll
    (if (pred (first coll))
      (cons (first coll)
            (filter pred (rest coll)))
      (filter pred (rest coll)))))

(defn reduce [f coll]
  (if (empty? (rest coll))
    (first coll)
    (reduce f
            (cons (f (first coll)
                     (first (rest coll)))
                  (rest (rest coll))))))


;; To-Add
;; - two styles of exponentiation
;; - two styles of fibbonocci
;; - flatten
;; - sort
;; - search
;; - contains?
;; - for
;; - group-by
;; - scan
;; - partition
;; - comp
;; - partial
;; - assoc / update / get / dissoc
;; - walk functions
;; - dedupe
