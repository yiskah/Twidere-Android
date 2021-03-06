package org.mariotaku.twidere.util;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;
import android.text.TextUtils;
import android.webkit.URLUtil;

import com.fasterxml.jackson.core.JsonParseException;

import org.mariotaku.microblog.library.MicroBlog;
import org.mariotaku.microblog.library.MicroBlogException;
import org.mariotaku.microblog.library.twitter.util.TwitterConverterFactory;
import org.mariotaku.restfu.ExceptionFactory;
import org.mariotaku.restfu.RestConverter;
import org.mariotaku.restfu.RestFuUtils;
import org.mariotaku.restfu.RestMethod;
import org.mariotaku.restfu.RestRequest;
import org.mariotaku.restfu.annotation.HttpMethod;
import org.mariotaku.restfu.http.Authorization;
import org.mariotaku.restfu.http.BodyType;
import org.mariotaku.restfu.http.Endpoint;
import org.mariotaku.restfu.http.HttpRequest;
import org.mariotaku.restfu.http.HttpResponse;
import org.mariotaku.restfu.http.MultiValueMap;
import org.mariotaku.restfu.http.RawValue;
import org.mariotaku.restfu.http.SimpleValueMap;
import org.mariotaku.restfu.http.ValueMap;
import org.mariotaku.restfu.http.mime.Body;
import org.mariotaku.restfu.oauth.OAuthEndpoint;
import org.mariotaku.restfu.oauth.OAuthToken;
import org.mariotaku.twidere.TwidereConstants;
import org.mariotaku.twidere.extension.model.AccountExtensionsKt;
import org.mariotaku.twidere.extension.model.CredentialsExtensionsKt;
import org.mariotaku.twidere.model.ConsumerKeyType;
import org.mariotaku.twidere.model.UserKey;
import org.mariotaku.twidere.model.account.cred.Credentials;
import org.mariotaku.twidere.model.util.AccountUtils;
import org.mariotaku.twidere.util.api.TwitterAndroidExtraHeaders;
import org.mariotaku.twidere.util.api.UserAgentExtraHeaders;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import kotlin.Pair;
import okhttp3.HttpUrl;

/**
 * Created by mariotaku on 15/5/7.
 */
public class MicroBlogAPIFactory implements TwidereConstants {

    public static final String CARDS_PLATFORM_ANDROID_12 = "Android-12";


    public static final SimpleValueMap sTwitterConstantPool = new SimpleValueMap();
    public static final SimpleValueMap sFanfouConstantPool = new SimpleValueMap();

    static {
        sTwitterConstantPool.put("include_cards", "true");
        sTwitterConstantPool.put("cards_platform", CARDS_PLATFORM_ANDROID_12);
        sTwitterConstantPool.put("include_my_retweet", "true");
        sTwitterConstantPool.put("include_rts", "true");
        sTwitterConstantPool.put("include_reply_count", "true");
        sTwitterConstantPool.put("include_descendent_reply_count", "true");
        sTwitterConstantPool.put("full_text", "true");
        sTwitterConstantPool.put("model_version", "7");
        sTwitterConstantPool.put("skip_aggregation", "false");
        sTwitterConstantPool.put("include_ext_alt_text", "true");
        sTwitterConstantPool.put("tweet_mode", "extended");

        sFanfouConstantPool.put("format", "html");
    }

    private MicroBlogAPIFactory() {
    }

    @WorkerThread
    public static MicroBlog getDefaultTwitterInstance(final Context context) {
        if (context == null) return null;
        final UserKey accountKey = Utils.getDefaultAccountKey(context);
        if (accountKey == null) return null;
        return getInstance(context, accountKey);
    }

    @WorkerThread
    public static MicroBlog getInstance(@NonNull final Context context, @NonNull final UserKey accountKey) {
        final AccountManager am = AccountManager.get(context);
        final Account account = AccountUtils.findByAccountKey(am, accountKey);
        if (account == null) return null;
        final Credentials credentials = AccountExtensionsKt.getCredentials(account, am);
        final String accountType = AccountExtensionsKt.getAccountType(account, am);
        return CredentialsExtensionsKt.newMicroBlogInstance(credentials, context, accountType,
                MicroBlog.class);
    }

    public static boolean verifyApiFormat(@NonNull String format) {
        final String url = getApiBaseUrl(format, "test");
        return URLUtil.isHttpsUrl(url) || URLUtil.isHttpUrl(url);
    }

    @NonNull
    public static String getApiBaseUrl(@NonNull String format, @Nullable final String domain) {
        final Matcher matcher = Pattern.compile("\\[(\\.?)DOMAIN(\\.?)\\]", Pattern.CASE_INSENSITIVE).matcher(format);
        final String baseUrl;
        if (!matcher.find()) {
            // For backward compatibility
            format = substituteLegacyApiBaseUrl(format, domain);
            if (!format.endsWith("/1.1") && !format.endsWith("/1.1/")) {
                baseUrl = format;
            } else {
                final String versionSuffix = "/1.1";
                final int suffixLength = versionSuffix.length();
                final int lastIndex = format.lastIndexOf(versionSuffix);
                baseUrl = format.substring(0, lastIndex) + format.substring(lastIndex + suffixLength);
            }
        } else if (TextUtils.isEmpty(domain)) {
            baseUrl = matcher.replaceAll("");
        } else {
            baseUrl = matcher.replaceAll("$1" + domain + "$2");
        }
        // In case someone set invalid base url
        if (HttpUrl.parse(baseUrl) == null) {
            return getApiBaseUrl(DEFAULT_TWITTER_API_URL_FORMAT, domain);
        }
        return baseUrl;
    }

    @NonNull
    private static String substituteLegacyApiBaseUrl(@NonNull String format, @Nullable String domain) {
        final int idxOfSlash = format.indexOf("://");
        // Not an url
        if (idxOfSlash < 0) return format;
        final int startOfHost = idxOfSlash + 3;
        if (startOfHost < 0) return getApiBaseUrl("https://[DOMAIN.]twitter.com/", domain);
        final int endOfHost = format.indexOf('/', startOfHost);
        final String host = endOfHost != -1 ? format.substring(startOfHost, endOfHost) : format.substring(startOfHost);
        final StringBuilder sb = new StringBuilder();
        sb.append(format.substring(0, startOfHost));
        if (host.equalsIgnoreCase("api.twitter.com")) {
            if (domain != null) {
                sb.append(domain);
                sb.append(".twitter.com");
            } else {
                sb.append("twitter.com");
            }
        } else if (host.equalsIgnoreCase("api.fanfou.com")) {
            if (domain != null) {
                sb.append(domain);
                sb.append(".fanfou.com");
            } else {
                sb.append("fanfou.com");
            }
        } else {
            return format;
        }
        if (endOfHost != -1) {
            sb.append(format.substring(endOfHost));
        }
        return sb.toString();
    }

    @NonNull
    public static String getApiUrl(@NonNull final String pattern, @Nullable final String domain, String appendPath) {
        String urlBase = getApiBaseUrl(pattern, domain);
        if (urlBase.endsWith("/")) {
            urlBase = urlBase.substring(0, urlBase.length() - 1);
        }
        if (appendPath == null) return urlBase + "/";
        if (appendPath.startsWith("/")) {
            appendPath = appendPath.substring(1);
        }
        return urlBase + "/" + appendPath;
    }

    @WorkerThread
    @Nullable
    public static ExtraHeaders getExtraHeaders(Context context, ConsumerKeyType type) {
        switch (type) {
            case TWITTER_FOR_ANDROID: {
                return new TwitterAndroidExtraHeaders();
            }
            case TWITTER_FOR_IPHONE: {
                return new UserAgentExtraHeaders("Twitter-iPhone");
            }
            case TWITTER_FOR_IPAD: {
                return new UserAgentExtraHeaders("Twitter-iPad");
            }
            case TWITTER_FOR_MAC: {
                return new UserAgentExtraHeaders("Twitter-Mac");
            }
            case TWEETDECK: {
                return new UserAgentExtraHeaders(UserAgentUtils.getDefaultUserAgentStringSafe(context));
            }
        }
        return null;
    }

    public static String getTwidereUserAgent(final Context context) {
        final PackageManager pm = context.getPackageManager();
        try {
            final PackageInfo pi = pm.getPackageInfo(context.getPackageName(), 0);
            return String.format("%s %s / %s", TWIDERE_APP_NAME, TWIDERE_PROJECT_URL, pi.versionName);
        } catch (final PackageManager.NameNotFoundException e) {
            throw new AssertionError(e);
        }
    }

    public static Endpoint getOAuthRestEndpoint(@NonNull String apiUrlFormat, boolean sameOAuthSigningUrl, boolean noVersionSuffix) {
        return getOAuthEndpoint(apiUrlFormat, "api", noVersionSuffix ? null : "1.1", sameOAuthSigningUrl);
    }

    public static Endpoint getOAuthSignInEndpoint(@NonNull String apiUrlFormat, boolean sameOAuthSigningUrl) {
        return getOAuthEndpoint(apiUrlFormat, "api", null, sameOAuthSigningUrl, true);
    }

    public static Endpoint getOAuthEndpoint(String apiUrlFormat, @Nullable String domain,
            @Nullable String versionSuffix,
            boolean sameOAuthSigningUrl) {
        return getOAuthEndpoint(apiUrlFormat, domain, versionSuffix, sameOAuthSigningUrl, false);
    }

    public static Endpoint getOAuthEndpoint(@NonNull String apiUrlFormat, @Nullable String domain,
            @Nullable String versionSuffix,
            boolean sameOAuthSigningUrl, boolean fixUrl) {
        String endpointUrl, signEndpointUrl;
        endpointUrl = getApiUrl(apiUrlFormat, domain, versionSuffix);
        if (fixUrl) {
            int[] authorityRange = UriUtils.getAuthorityRange(endpointUrl);
            if (authorityRange != null && endpointUrl.regionMatches(authorityRange[0],
                    "api.fanfou.com", 0, authorityRange[1] - authorityRange[0])) {
                endpointUrl = endpointUrl.substring(0, authorityRange[0]) + "fanfou.com" +
                        endpointUrl.substring(authorityRange[1]);
            }
        }
        if (!sameOAuthSigningUrl) {
            signEndpointUrl = getApiUrl(DEFAULT_TWITTER_API_URL_FORMAT, domain, versionSuffix);
        } else {
            signEndpointUrl = endpointUrl;
        }
        return new OAuthEndpoint(endpointUrl, signEndpointUrl);
    }

    public static OAuthToken getOAuthToken(String consumerKey, String consumerSecret) {
        if (isValidConsumerKeySecret(consumerKey) && isValidConsumerKeySecret(consumerSecret))
            return new OAuthToken(consumerKey, consumerSecret);
        return new OAuthToken(TWITTER_CONSUMER_KEY, TWITTER_CONSUMER_SECRET);
    }

    public static boolean isValidConsumerKeySecret(@NonNull CharSequence text) {
        for (int i = 0, j = text.length(); i < j; i++) {
            if (!isAsciiLetterOrDigit(text.charAt(i))) return false;
        }
        return true;
    }

    private static boolean isAsciiLetterOrDigit(int codePoint) {
        return 'A' <= codePoint && codePoint <= 'Z' || 'a' <= codePoint && codePoint <= 'z'
                || '0' <= codePoint && codePoint <= '9';
    }

    public static class TwidereHttpRequestFactory implements HttpRequest.Factory {

        @Nullable
        private final ExtraHeaders extraHeaders;

        public TwidereHttpRequestFactory(final @Nullable ExtraHeaders extraHeaders) {
            this.extraHeaders = extraHeaders;
        }

        @Override
        public <E extends Exception> HttpRequest create(@NonNull Endpoint endpoint, @NonNull RestRequest info,
                @Nullable Authorization authorization,
                RestConverter.Factory<E> converterFactory)
                throws IOException, RestConverter.ConvertException, E {
            final String restMethod = info.getMethod();
            final String url = Endpoint.constructUrl(endpoint.getUrl(), info);
            MultiValueMap<String> headers = info.getHeaders();
            if (headers == null) {
                headers = new MultiValueMap<>();
            }

            if (authorization != null && authorization.hasAuthorization()) {
                headers.add("Authorization", RestFuUtils.sanitizeHeader(authorization.getHeader(endpoint, info)));
            }
            if (extraHeaders != null) {
                for (final Pair<String, String> pair : extraHeaders.get()) {
                    headers.add(pair.getFirst(), RestFuUtils.sanitizeHeader(pair.getSecond()));
                }
            }
            return new HttpRequest(restMethod, url, headers, info.getBody(converterFactory), null);
        }
    }

    public static class TwidereExceptionFactory implements ExceptionFactory<MicroBlogException> {

        private final TwitterConverterFactory converterFactory;

        public TwidereExceptionFactory(TwitterConverterFactory converterFactory) {
            this.converterFactory = converterFactory;
        }

        @Override
        public MicroBlogException newException(Throwable cause, HttpRequest request, HttpResponse response) {
            final MicroBlogException te;
            if (cause != null) {
                te = new MicroBlogException(cause);
            } else {
                te = parseTwitterException(response);
            }
            te.setHttpRequest(request);
            te.setHttpResponse(response);
            return te;
        }


        public MicroBlogException parseTwitterException(HttpResponse resp) {
            try {
                return (MicroBlogException) converterFactory.forResponse(MicroBlogException.class).convert(resp);
            } catch (JsonParseException e) {
                return new MicroBlogException("Malformed JSON Data", e);
            } catch (IOException e) {
                return new MicroBlogException("IOException while throwing exception", e);
            } catch (RestConverter.ConvertException e) {
                return new MicroBlogException(e);
            } catch (MicroBlogException e) {
                return e;
            }
        }
    }

    public static class TwidereRestRequestFactory implements RestRequest.Factory<MicroBlogException> {
        @Nullable
        private final Map<String, String> extraRequestParams;

        public TwidereRestRequestFactory(@Nullable Map<String, String> extraRequestParams) {
            this.extraRequestParams = extraRequestParams;
        }

        @Override
        public RestRequest create(RestMethod<MicroBlogException> restMethod,
                RestConverter.Factory<MicroBlogException> factory,
                ValueMap valuePool) throws RestConverter.ConvertException, IOException, MicroBlogException {
            final HttpMethod method = restMethod.getMethod();
            final String path = restMethod.getPath();
            final MultiValueMap<String> headers = restMethod.getHeaders(valuePool);
            final MultiValueMap<String> queries = restMethod.getQueries(valuePool);
            final MultiValueMap<Body> params = restMethod.getParams(factory, valuePool);
            final RawValue rawValue = restMethod.getRawValue();
            final BodyType bodyType = restMethod.getBodyType();
            final Map<String, Object> extras = restMethod.getExtras();

            if (queries != null && extraRequestParams != null) {
                for (Map.Entry<String, String> entry : extraRequestParams.entrySet()) {
                    queries.add(entry.getKey(), entry.getValue());
                }
            }

            return new RestRequest(method.value(), method.allowBody(), path, headers, queries,
                    params, rawValue, bodyType, extras);
        }
    }

    public interface ExtraHeaders {
        @NonNull
        List<Pair<String, String>> get();
    }
}
