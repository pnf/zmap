(ns zmap.core
  ( :gen-class
    :main true)
  (:require [clj-http.client :as client])
  (:require [zmap.java-utils :as ju])
  (:require [ clojure.core.async :as async :refer [<!! >!! timeout chan alt!! go]])
  (:import (javax.mail Message MessagingException Session Folder Flags)
           (javax.mail.search FlagTerm)
           (javax.mail Flags$Flag)
           (javax.mail.internet InternetAddress MimeMessage)
           (com.icegreen.greenmail.user UserException UserImpl)
           (com.icegreen.greenmail.util GreenMail ServerSetupTest DummySSLSocketFactory)
           (org.apache.commons.mail.util MimeMessageParser)
           (com.sun.mail.smtp.SMTPTransport)))



(defn store [protocol server port user pass]
  (let [;sf (com.sun.mail.util.MailSSLSocketFactory.)
        ;_  (.setTrustAllHosts sf true)
        p (ju/as-properties [["mail.store.protocol" protocol]
                             ;["mail.imaps.host" server]
                             ;["mail.imaps.port" (str port)]
                             ;["mail.imaps.ssl.socketFactory",sf]
                             ;["mail.imap.starttls.enable" "true"]
                             ;["mail.imaps.ssl.checkserveridentity" "false"]
                             ;["mail.imaps.ssl.trust" "*"]
]
)]
    (doto (.getStore (Session/getDefaultInstance p) protocol)
          (.connect server port user pass))))

(defn folders 
  "Get list of folders for  a store"
  ([s] (folders s (.getDefaultFolder s)))
  ([s f]
     (let [sub? #(if (= 0 (bit-and (.getType %) 
                                   Folder/HOLDS_FOLDERS)) false true)]
       (map #(cons (.getName %) (if (sub? %) (folders s %))) (.list f)))))

(defn messages [s fd & opt]
  "Get list of messages in folder fd in store s"
  (let [fd (doto (.getFolder s fd) (.open Folder/READ_ONLY))
        [flags set] opt
        msgs (if opt 
               (.search fd (FlagTerm. (Flags. flags) set)) 
               (.getMessages fd))]
    (map #(vector (.getUID fd %) %) msgs)))

;(messages lh "ZULIP-Private")
;(.delete (.getFolder lh "clojure.lang.LazySeq@f75de046") true)


(defn ensure-folder [s fd]
  "Make sure folder fd exists in store s"
  (let [f (.getFolder s fd)]
    (if (.exists f) f
        (do 
          (println "Creating folder" f)
          (.create f Folder/HOLDS_MESSAGES) f))))


; create possibly fake email address into InternetAddressArray
(defn encode-from [from]
  (let [email  (re-find #"\b[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,4}\b" from)
        email  (or email (str from "@stream.com"))
        email  (InternetAddress. email)
        email  (into-array (type email) [email] )]
    email ))

(defn create-mime-message [& {:keys [subject text from reply-to]
                              :or   {reply-to nil}}]
  (let [mm   (com.icegreen.greenmail.util.ZGreenMail/newEmptyMimeMessage)]
    ;(println subject text from reply-to)
    (.addFrom mm (encode-from from))
    (.setSubject mm subject)
    (.setText mm text)
    (if reply-to (.setReplyTo mm (encode-from reply-to)))
    mm
    ))

(defn append-messages [s fd mms]
  (.appendMessages (ensure-folder s fd) mms))

(defn append-message [s fd mm]
  (append-messages s fd (into-array (type mm) [mm])))

(defn decode-to [to]
  (if-let [email  (re-find #"\b[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,4}\b" to)]
    (if-let [stream (re-find #"(.*)@stream.com" email)]
      [false  (second stream)]
      [true  email])
    [false to]
))


(defn send-to-humbug [ks [subject to content]]
  (let [[is-private to] (decode-to to)
        params (if is-private
                 {"type"    "private"
                  "to"      to
                  "content" (str subject content)}
                 {"type"    "stream"
                  "to"      to
                  "subject" subject
                  "content" content}
                 )]
    (println (clojure.string/join ";" [subject to content]))
    (println  (client/post  "https://api.humbughq.com/v1/messages"
                            {:basic-auth ks
                             :query-params params
                             }
                            ))
    ))



;(def gmail (store "imaps" "imap.gmail.com" 
;                  "nurullah@nakkaya.com" "super_secret_pass"))

;; curl -G  https://api.humbughq.com/v1/messages/latest\
;;  -u othello-bot@example.com:a0b1c2d3e4f5a6b7c8d9e0f1a2b3c4d5 \
;;  -d "last=102345"
;; Example response
;; { "messages" : [ { "client" : "website",
;;         "content" : "hi",
;;         "content_type" : "text/x-markdown",
;;         "display_recipient" : [ { "email" : "wdaher@example.com",
;;               "full_name" : "Waseem Daher",
;;               "short_name" : "wdaher"
;;             },
;;             { "email" : "othello-bot@example.com",
;;               "full_name" : "Othello, Moor of Venice",
;;               "short_name" : "othello-bot"
;;             }
;;           ],
;;         "gravatar_hash" : "948fcdfa93dd8986106032f1bad7f2c8",
;;         "id" : 400,
;;         "recipient_id" : 101,
;;         "sender_email" : "othello-bot@example.com",
;;         "sender_full_name" : "Othello, Moor of Venice",
;;         "sender_short_name" : "othello-bot",
;;         "subject" : "",
;;         "timestamp" : 1365532669,
;;         "type" : "private"
;;       } ],
;;   "msg" : "",
;;   "result" : "success",
;;   "update_types" : [ "new_messages" ]
;; }



  (defn fetch-new-zulip-messages [ks last]
    (do 
          (println "Fetching from " last)
          (let [params {:basic-auth ks :as :json}
                params (if last (assoc params :query-params {:last last}) params)
                resp   (client/get "https://api.humbughq.com/v1/messages/latest" params)
                ]
            ;(println "Got " resp)
            (-> resp :body :messages)
            ))
    )


  (defn insert-message [store m]
    (let [private? (= (:type m) "private")
          dr       (:display_recipient m)
          folder   (str (if private? "ZULIP-Private" (str "ZULIP-" dr)))
          stream   (if private? nil dr)
          mm       (create-mime-message :subject (:subject m) 
                                        :text (:content m)
                                        :from (:sender_email m) 
                                        :reply-to stream)]
      (println "Storing in" folder)
      (append-message store folder mm)))

(defn -main []

  (def auth
    (let [rc   (slurp (str (System/getenv "HOME") "/.humbugrc"))
          kvs  (mapcat (fn [[_ k v]] [k v])   (re-seq #"\b([a-z]+)\s*=\s*(\S+)\b" rc))]
      (apply hash-map kvs)))
  (def ks (str (auth "email") ":" (auth "key")))

  (def imapHostManager (com.icegreen.greenmail.imap.ImapHostManagerImpl.))
  (def userManager (com.icegreen.greenmail.user.UserManager. imapHostManager))
  (def imap (store "imap" 
                   (auth "imaphost")
                   (Integer/parseInt (auth "imapport"))
                   (auth "imapuser")
                   (auth "imappassword")))

  ;; Process send requests asynchronously
  (def send-channel (chan))
  (go (while true (let [state (<! send-channel)]
                    (println state)
                    (send-to-humbug ks state)
                    )))

  (defn send-to-humbug-async [channel state]
    (try (let [mm       (.getMessage state)
               m        (.getMessage mm)
               p        (MimeMessageParser. m)
               _        (.parse p)
               subject  (.getSubject p)
               to       (.toString (first (.getTo p)))
               content (.getPlainContent p)]
           (go (>! send-channel [subject to content]))
           nil)
         (catch Exception e ((let [em (.getMessage e)]
                               (println em)
                                          em)))))

  (def smtpManager (proxy 
                       [com.icegreen.greenmail.smtp.SmtpManager] 
                       [imapHostManager userManager]
                     (checkData [state] (send-to-humbug-async send-channel state))))
  (def managers (proxy [com.icegreen.greenmail.Managers] []
                  (getSmtpManager [] smtpManager)))
  (def mailServer (com.icegreen.greenmail.util.ZGreenMail. ServerSetupTest/SMTP managers))
  (.start mailServer)
  (def user (.setUser mailServer "user@host.com" "user" "user"))

  (go (loop [last nil]
        (recur 
         (try
           (let [ms (fetch-new-zulip-messages ks last)]  ;; this is actually going to block
             (doseq [m ms] (insert-message imap m))
             (apply max (map :id ms))
             )
           (catch Exception e (let [em (.getMessage e)]
                              (println "Sleeping after exception: " em)
                              (Thread/sleep 1000)
                              nil))
         ))))


  (println "Mail server started")

)
