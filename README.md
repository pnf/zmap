# zmap

A tool for making humbug/zulip available through IMAP and hence through emacs.

## Usage

In addition to this code (and a zulip acount), you will need an
account on an IMAP server.  In this account messages will be inserted
into auto-created folders with names like ZULIP-streamname.  The
clojure program will also expose an SMTP service on 3025 that accepts
messages and transfers them to zulip: the recipient should be a zulip
email id for a personal message or `STREAMNAME@stream.com` for a stream message.
If your mail apparatus supports it, the `stream.com` can be left off.

On linux, it is particularly easy to run an imap server:  First `apt-get install`
(or whatever) `dovecot-imapd`.  Then, if you want to make your life easier at the
expense of a little insecurity, add to `/etc/dovecot/dovecot.con`
~~~
mail_location = mbox:~/mail:INBOX=/var/mail/%u
disable_plaintext_auth = no
~~~

Credentials for your IMAP server can be stored in the `~/.humbugrc` file that's used
for the python interface.  The file will end up looking like:

~~~
key=YOUR_ZULIP_API_KEY
email=YOUR_ZULIP_EMAIL_ID
imaphost=...
imapport=..
imapuser=...
imappassword=...
~~~

If you do decide to expose all this in emacs, GNUS is a good way to go.  Here's a
reasonable `.gnus` file that gives you access to gmail as well:

~~~

(setq read-mail-command 'gnus
      gnus-agent-go-online t
      gnus-inhibit-startup-message t
      gnus-large-newsgroup nil
      gnus-nntp-server nil
      gnus-select-method '(nnimap "gmail"
				(nnimap-address "imap.gmail.com")
				(nnimap-server-port 993)
				(nnimap-stream ssl)
				(nnimap-authinfo-file "~/authinfo"))
      gnus-secondary-select-methods '((nnimap "local"
					      (nnimap-address "localhost")  ;; your info here
					      (nnimap-server-port 2143)
					      (nnimap-stream network)))

      gnus-summary-line-format "%U%R%z%B%(%[%4L: %-23,23f%]%) %s\n")

(setq smtpmail-smtp-server "smtp.gmail.com"
      smtpmail-smtp-service 587
      smtpmail-auth-credentials '(("smtp.gmail.com" 587
                                   "peter.fraenkel@gmail.com" nil))
      smtpmail-starttls-credentials '(("smtp.gmail.com" 587 nil nil)))

(setq message-send-mail-function 'message-send-mail-with-sendmail)
(setq sendmail-program "/usr/local/bin/msmtp")
(setq gnus-permanently-visible-groups ".*")
(setq mail-host-address "podsnap.com")

(setq gnus-posting-styles '((".*"
			     (eval (setq message-sendmail-extra-arguments '("-a" "gmail"))))
			    (".*ZULIP.*"
			     (eval (setq message-sendmail-extra-arguments '("-a" "local"))))))
 
(setq gnus-parameters '((".*"
			 (display . all)
			 (posting-style)
			 (name "Peter Fraenkel")
			 (address "peter.fraenkel@gmail.com"))
			(".*ZULIP.*"
			 (display . all)
			 (posting-style)
			 (name "Peter Fraenkel")
			 (address "pnf@podsnap.com.com")
			 )))
~~~

This setup requires you to have installed `msmtp` and `gnutls`.  The former is
configured in `~/.msmtprc`, which will look like

~~~
defaults
tls on
auto_from on
logfile ~/.msmtp.log

account gmail
host smtp.gmail.com
tls on
tls_certcheck off
auth on
from pnf@podsnap.com
user peter.fraenkel@gmail.com
password YOUR_APPLICATION_SPECIFIC_PASSWORD
port 587

account local  ; your details below instead of mine
host localhost
tls off
tls_certcheck off
auth off
from pnf@zulip.com
user pnf
password blehisnotagoodpassword
port 3025
~~~
and you'll have additional top-secret information in `~/.authinfo`:
~~~
machine imap.gmail.com login peter.fraenkel@gmail.com password YOUR_APPLICATION_SPECIFIC_PASSWORD port 993
machine localhost login pnf password blehorsomethingsimilar port 2143
~~~

Some of the blather in `.gnus` is to switch to the appropriate SMTP server based on
the GNUS folder you happen to be in at the moment. Obviously there a ways for it to break.

If your happy with the setup, then `lein run` will start the server process.


## License

Distributed under the Eclipse Public License, the same as Clojure.
