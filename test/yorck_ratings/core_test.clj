(ns yorck-ratings.core-test
  (:use org.httpkit.fake
        hickory.core)
  (:require [clojure.test :refer :all]
            [yorck-ratings.core :refer :all]
            [clojure.java.io :as io]
            [clojure.core.async :as a]))

(defn load-fixture [filename]
  (->> filename
       (str "fixtures/")
       (io/resource)
       (slurp)))

(def yorck-list-fixture (load-fixture "yorck_list.html"))
(def parsed-yorck-list-fixture (as-hickory (parse yorck-list-fixture)))
(def hateful-8-dp-fixture (load-fixture "hateful_8_detail_page.html"))
(def hateful-8-sp-fixture (load-fixture "hateful_8_search_page.html"))
(def parsed-hateful-8-sp-fixture (as-hickory (parse hateful-8-sp-fixture)))
(def parsed-hateful-8-dp-fixture (as-hickory (parse hateful-8-dp-fixture)))
(def carol-dp-fixture (load-fixture "carol_detail_page.html"))
(def carol-sp-fixture (load-fixture "carol_search_page.html"))
(def yorck-list-url "https://www.yorck.de/filme?filter_today=true")

(deftest end-to-end-test
  (testing "return rated movie infos ordered by rating"
    (with-fake-http [yorck-list-url yorck-list-fixture
                     "https://m.imdb.com/find?q=The+Hateful+8" hateful-8-sp-fixture
                     "https://m.imdb.com/title/tt3460252/" hateful-8-dp-fixture
                     "https://m.imdb.com/find?q=Carol" carol-sp-fixture
                     "https://m.imdb.com/title/tt2402927/" carol-dp-fixture]
                    (let [expected [(make-rated-movie {:rating       8.0
                                                       :rating-count 123336
                                                       :imdb-title   "The Hateful Eight"
                                                       :imdb-url     "https://m.imdb.com/title/tt3460252/"
                                                       :yorck-title  "The Hateful 8"
                                                       :yorck-url    "https://www.yorck.de/filme/hateful-8-the"})
                                    (make-rated-movie {:rating       7.6
                                                       :rating-count 22728
                                                       :imdb-title   "Carol"
                                                       :imdb-url     "https://m.imdb.com/title/tt2402927/"
                                                       :yorck-title  "Carol"
                                                       :yorck-url    "https://www.yorck.de/filme/carol"})]
                          actual (atom [])]
                      (rated-movies (fn [movies] (swap! actual concat movies)))
                      (Thread/sleep 500)
                      (is (= @actual expected))))))

(deftest async-get-test
  (testing "writes parsed successful get request result to channel"
    (with-fake-http [yorck-list-url yorck-list-fixture]
                    (let [result-ch (a/chan 1)
                          error-ch (a/chan 1)]
                      (async-get yorck-list-url result-ch error-ch)
                      (is (= parsed-yorck-list-fixture (a/<!! result-ch))))))

  (testing "writes request error to error channel"
    (let [result-ch (a/chan 1)
          error-ch (a/chan 1)
          expected "Error fetching URL \"http://non-existant-url.kentucky\": non-existant-url.kentucky: unknown error"]
      (async-get "http://non-existant-url.kentucky" result-ch error-ch)
      (is (= expected (a/<!! error-ch)))))

  (testing "writes successful request with exceptional response to error channel"
    (with-fake-http ["http://m.imdb.com/non-existant-uri" 404]
                    (let [result-ch (a/chan 1)
                          error-ch (a/chan 1)
                          expected "Error fetching URL \"http://m.imdb.com/non-existant-uri\": 404"]
                      (async-get "http://m.imdb.com/non-existant-uri" result-ch error-ch)
                      (is (= expected (a/<!! error-ch)))))))

(deftest yorck-titles-test
  (testing "returns yorck movie titles"
    (let [expected ["Carol" "The Hateful 8"]]
      (is (= expected (yorck-titles parsed-yorck-list-fixture))))))

(deftest yorck-urls-test
  (testing "returns yorck movie urls"
    (let [expected ["https://www.yorck.de/filme/carol"
                    "https://www.yorck.de/filme/hateful-8-the"]]
      (is (= expected (yorck-urls parsed-yorck-list-fixture))))))

(deftest yorck-sp-infos-test
  (testing "returns yorck movie titles and urls"
    (let [expected [(make-rated-movie {:yorck-title "Carol"
                                       :yorck-url   "https://www.yorck.de/filme/carol"})
                    (make-rated-movie {:yorck-title "The Hateful 8"
                                       :yorck-url   "https://www.yorck.de/filme/hateful-8-the"})]]
      (is (= expected (yorck-titles-urls parsed-yorck-list-fixture))))))

(deftest imdb-titles-test
  (testing "returns imdb movie titles"
    (let [expected "The Hateful Eight"]
      (is (= expected (imdb-title parsed-hateful-8-sp-fixture))))))

(deftest imdb-urls-test
  (testing "returns imdb movie urls"
    (let [expected "https://m.imdb.com/title/tt3460252/"]
      (is (= expected (imdb-url parsed-hateful-8-sp-fixture))))))

(deftest imdb-titles-urls-test
  (testing "returns imdb movie titles"
    (let [expected {:imdb-title "The Hateful Eight"
                    :imdb-url   "https://m.imdb.com/title/tt3460252/"}]
      (is (= expected (imdb-sp-infos parsed-hateful-8-sp-fixture))))))

(deftest imdb-rating-test
  (testing "returns imdb movie rating"
    (let [expected 8.0]
      (is (= expected (imdb-rating parsed-hateful-8-dp-fixture))))))

(deftest imdb-rating-count-test
  (testing "returns imdb movie rating count"
    (let [expected 123336]
      (is (= expected (imdb-rating-count parsed-hateful-8-dp-fixture))))))

(deftest rotate-article-test
  (testing "fixes Yorck titles with their article at the end"
    (is (= "The Hateful 8" (rotate-article "Hateful 8, The")))
    (is (= "Hail, Caesar!" (rotate-article "Hail, Caesar!")))
    (is (= "Das Brandneue Testament" (rotate-article "Brandneue Testament, Das")))
    (is (= "Der Unterhändler" (rotate-article "Unterhändler, Der")))
    (is (= "Die Winzlinge - Operation Zuckerdose" (rotate-article "Winzlinge, Die - Operation Zuckerdose"))))

  (testing "leaves titles without article untouched"
    (is (= "Carol" (rotate-article "Carol")))))