(ns scrollsync.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.core.async :refer [put! <! chan]]
            [reagent.core :as r]))

(defn get-el
  [id]
  (.getElementById js/document id))

(defn get-el-y
  [id]
  (when-let [el (get-el id)]
    (aget (.getBoundingClientRect el) "top")))

(defn assoc-positions
  [bp]
  (assoc bp :pos [(get-el-y (:id bp))
                  (:break-at bp)]))

(defn get-broken-bps
  [bps]
  (filter #(< (first (:pos %))
              (second (:pos %)))
          bps))

(defn get-offset-diff
  [pos]
  (- (first pos) (second pos)))

(defn get-active-bp
  [bps]
  (reduce
    (fn [a b]
      (if (< (get-offset-diff a)
             (get-offset-diff b))
        a b))
    (get-broken-bps bps)))

(defn create-bp-handler
  [bps c]
  (fn [_]
    (when-not (nil? (get-el (:id (first bps))))
      (put! c (or (:params (get-active-bp (map assoc-positions bps)))
                  {})))))

(defn scroll->chan
  [el c state]
  (.addEventListener el "scroll" (:handler @state)) c)


(defn scrollsync
  "
  @param {fn} :bp-fn (breakpoint function)
    the function called when a breakpoint is triggered

  @param {collection} :breakpoints
    a collection of maps corresponding to each breakpoint
    {:id the breakpoint element's id
     :params the object passed to :bp-fn when the breakpoint is triggered
     :break-at the element's offset from the top of the window at which the trigger fires. (i.e. a value of 0 would mean the trigger fires when the top of the element reaches the top of the window.)}

  @param {hiccup/jsx} :content
    scrollsync's child component, breakpoint elements should be placed here so that the event listener is removed on component-will-unmount
  "
  [& {:keys [breakpoints bp-fn content]}]
  (let [c (chan 1)
        scrollsync-state (r/atom {:handler (create-bp-handler breakpoints c)
                                  :last-params nil})
        scrollsync! (fn []
                      (let [chan (scroll->chan js/window c scrollsync-state)]
                        (go (while true
                              (let [params (<! chan)]
                                (if (not= params (:last-params @scrollsync-state))
                                  (do
                                    (bp-fn params)
                                    (swap! scrollsync-state assoc :last-params params))))))))]
    (r/create-class
      {:component-did-mount #(scrollsync!)
       :component-will-unmount #(.removeEventListener js/window "scroll" (:handler @scrollsync-state))
       :component-will-receive-props (fn [this [_ & new-args]]
                                       (let [new-bps (second new-args)]
                                         (.removeEventListener js/window "scroll" (:handler @scrollsync-state))
                                         (swap! scrollsync-state assoc :handler (create-bp-handler new-bps c))
                                         (scrollsync!)))
       :reagent-render (fn [& {:keys [breakpoints bp-fn content]}]
                         [:div content])})))
