#Cloudpokes

**Overview**

Cloudpokes is a simple, lightweight push notification library for Java
backends of iOS and Android apps. A single SSL connection to APNS is
kept alive, making Cloudpokes suitable for apps with medium load.
Cloudpokes provides a platform-abstracted push API that makes serving iOS
and Android clients from the same backend simple and easy.

Payloads are constructed and notifications routed transparently depending
on client type, which is encoded inside a <code>DeviceToken</code> class.
This allows a factory to instantiate the correct platform-specific
implementation of a <code>Notification</code>. Therefore, the following
example will send a greeting to the device identified by
<code>token</code>, no matter whether it is an iOS or an Android device:
```java
void sayHello(String name, DeviceToken token) {
    Notification notification = Notification.withToken(token);
    notification.setMessage("Hello " + name + '!')
            .setDefaultSound().send();
}
```

**Configuration**

To configure the APNS gateway, you must implement the
<code>ApnsGateway.ApnsConfig</code> abstract class with methods
<code>getCertFile()</code> and <code>getCertPhrase()</code>. These
methods must return an <code>InputStream</code> of your app's PKCS #12
push certificate and its passphrase, respectively, for example:
```java
ApnsConfig apns_config = new ApnsConfig(Env.SANDBOX) {
    @Override
    public InputStream getCertFile() throws IOException {
        return getResourceStream("META-INF/apns-dev.p12");
    }
    @Override
    public String getCertPhrase() {
        return "changeme";
    }
};
ApnsPushSender.configure(apns_config);
```
The constructor of <code>ApnsConfig</code> takes an environment specifier
which can be <code>Env.PRODUCTION</code> or <code>Env.SANDBOX</code>. The
same configuration object can be used for setting up the
<code>FeedbackClient</code>, which queries the APNS feedback service.

To configure the GCM gateway, implement the <code>getApiKey()</code>
method of the <code>GcmPushSender.Delegate</code> interface and return
your API key. A good place to configure both gateways is the
<code>init</code> method of your <code>HttpServlet</code>.

**Custom JSON payloads**

Cloudpokes internally uses Ralf Sternberg's highly efficient
*minimal-json* to build platform-conforming JSON payloads.
<code>Notification</code> also exposes functionality to extend payload
objects with custom members. Use the method <code>setCustom(String key,
JsonValue value)</code> to insert such members, which can be atomic,
array, or object values. <code>key</code> can be any JSON-key except
<code>"aps"</code>, which is reserved by iOS. For iOS, custom values are
stored as members of the main JSON payload, whereas for Android, those
values become members of the <code>"data"</code> object.

**Error handling**

Currently, only rudimentary support exists in Cloudpokes for detecting
whether a specific Notification was rejected by the APNS endpoint.
However, a full client for the APNS feedback service is implemented as
<code>FeedbackClient</code>, which takes an <code>ApnsConfig</code>
object for construction. To receive inactive tokens from APNS, you
must implement the <code>FeedbackClient.Listener</code> interface, for
example:
```java
FeedbackClient.Listener listener = new FeedbackClient.Listener() {
    @Override
    public void receiveInactiveToken(byte[] token) {
        removeTokenFromUserDB(token);
    }
};
FeedbackClient feedback_client = new FeedbackClient(apns_config);
try {
    feedback_client.fetchInactiveTokens(listener);
}
catch (IOException e) { ... }
```
In contrast, transmission errors at the GCM endpoint are directly
reported to the <code>GcmPushSender.Delegate</code> interface that you
must implement. The corresponding methods are <code>didSend</code> and
<code>didFail</code>, for example:
```java
@Override
public void didSend(Notification notification, String reg_id) {
    if (reg_id != null) {
        updateToken(notification.getToken().getGcmToken(), reg_id);
    }
}
```
In the above, <code>reg_id</code> is the canonical registration ID from
the GCM endpoint if one was returned or <code>null</code> otherwise.
The failure handler could look like this:
```java
@Override
public void didFail(Notification notification, String error) {
    if ("NotRegistered".equals(error)) {
        removeTokenFromUserDB(notification.getToken().getGcmToken());
    }
}
```
See the Android Developers reference for a list of possible
<code>error</code> strings. <code>error</code> is <code>null</code> for
failed connections to the GCM endpoint.

**How to contribute**

* Better support for detecting errors at the APNS endpoint and automatic
  resending of those notifications that were streamed after the offending
  one and before APNS hung up.
* This would also allow efficient support for broadcasts.
* Support for Windows Phone via MPNS.
* This library is not yet documented with Javadoc annotations. Javadocs
  for at least all <code>public</code> would be very useful.
