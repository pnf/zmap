(defproject zmap "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [com.icegreen/greenmail "1.3.1b"]
                 [org.apache.commons/commons-email "1.3.1"]
                 [clj-http "0.7.6"]
                 [local/core-async "0.1.0-SNAPSHOT"]
]
  :java-source-paths ["src/java"]
  :main zmap.core
)

