package fi.nls.oskari.control.layer;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import fi.nls.oskari.annotation.OskariActionRoute;
import fi.nls.oskari.control.*;
import fi.nls.oskari.domain.map.OskariLayer;
import fi.nls.oskari.log.LogFactory;
import fi.nls.oskari.log.Logger;
import fi.nls.oskari.map.layer.formatters.LayerJSONFormatterVectorTile;
import fi.nls.oskari.service.OskariComponentManager;
import fi.nls.oskari.util.IOHelper;
import fi.nls.oskari.util.JSONHelper;
import fi.nls.oskari.util.PropertyUtil;

import org.json.JSONArray;
import org.json.JSONObject;
import org.oskari.permissions.PermissionService;
import org.oskari.service.user.LayerAccessHandler;
import org.oskari.service.util.ServiceFactory;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.net.HttpURLConnection;
import java.util.*;

import fi.nls.oskari.service.capabilities.CapabilitiesConstants;
import static fi.nls.oskari.control.ActionConstants.KEY_ID;
import static fi.nls.oskari.map.layer.formatters.LayerJSONFormatter.KEY_LEGENDS;
import static fi.nls.oskari.map.layer.formatters.LayerJSONFormatter.KEY_GLOBAL_LEGEND;


@OskariActionRoute("GetLayerTile")
public class GetLayerTileHandler extends ActionHandler {

    private static final Logger LOG = LogFactory.getLogger(GetLayerTileHandler.class);
    private static final String LEGEND = "legend";
    private static final String NAME = "name";
    private static final List<String> RESERVED_PARAMETERS = Arrays.asList(new String[]{KEY_ID, ActionControl.PARAM_ROUTE, LEGEND});
    private static final int TIMEOUT_CONNECTION = PropertyUtil.getOptional("GetLayerTile.timeout.connection", 1000);
    private static final int TIMEOUT_READ = PropertyUtil.getOptional("GetLayerTile.timeout.read", 5000);
    private static final boolean GATHER_METRICS = PropertyUtil.getOptional("GetLayerTile.metrics", true);
    private static final String METRICS_PREFIX = "Oskari.GetLayerTile";
    private PermissionHelper permissionHelper;
    private Collection<LayerAccessHandler> layerAccessHandlers;

    // WMTS rest layers params
    private static final String KEY_STYLE = "STYLE";
    private static final String KEY_TILEMATRIXSET = "TILEMATRIXSET";
    private static final String KEY_TILEMATRIX = "TILEMATRIX";
    private static final String KEY_TILEROW = "TILEROW";
    private static final String KEY_TILECOL = "TILECOL";

    /**
     *  Init method
     */
    public void init() {
        permissionHelper = new PermissionHelper(ServiceFactory.getMapLayerService(), OskariComponentManager.getComponentOfType(PermissionService.class));

        Map<String, LayerAccessHandler> handlerComponents = OskariComponentManager.getComponentsOfType(LayerAccessHandler.class);
        this.layerAccessHandlers = handlerComponents.values();
    }

    /**
     * Action handler
     * @param params Parameters
     * @throws ActionException
     */
    public void handleAction(final ActionParameters params)
            throws ActionException {

        // Resolve layer
        final int layerId = params.getRequiredParamInt(KEY_ID);
        final OskariLayer layer = permissionHelper.getLayer(layerId, params.getUser());

        final MetricRegistry metrics = ActionControl.getMetrics();

        Timer.Context actionTimer = null;
        if(GATHER_METRICS) {
            final com.codahale.metrics.Timer timer = metrics.timer(METRICS_PREFIX + "." + layerId);
            actionTimer = timer.time();
        }

        String httpMethod = params.getRequest().getMethod();
        String url;
        String postParams = null;
        boolean doOutPut = httpMethod.equals("POST");
        if (doOutPut) {
            url = layer.getUrl();
            postParams = IOHelper.getParams(getUrlParams(params.getRequest()));
        } else {
            url = getURL(params, layer);
        }
        // TODO: we should handle redirects here or in IOHelper or start using a lib that handles 301/302 properly
        HttpURLConnection con = getConnection(url, layer);

        layerAccessHandlers.forEach(handler -> handler.handle(layer, params.getUser()));

        try {
            con.setRequestMethod(httpMethod);
            con.setDoOutput(doOutPut);
            con.setConnectTimeout(TIMEOUT_CONNECTION);
            con.setReadTimeout(TIMEOUT_READ);
            con.setDoInput(true);
            con.setFollowRedirects(true);
            con.setUseCaches(false);
            // tell the service who is making the requests
            IOHelper.addIdentifierHeaders(con);
            con.connect();

            if (doOutPut) {
                IOHelper.writeToConnection(con, postParams);
            }

            final int responseCode = con.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                // prevent excessive logging by handling a common case where service responds with 404
                params.getResponse().sendError(HttpServletResponse.SC_NOT_FOUND);
                LOG.debug("URL reported 404:", url);
                return;
            }
            final String contentType = con.getContentType().toLowerCase();
            if(responseCode != HttpURLConnection.HTTP_OK || !isContentTypeOK(contentType)) {
                LOG.warn("URL", url, "returned HTTP response code", responseCode,
                        "with message", con.getResponseMessage(), "and content-type:", contentType);
                String msg = IOHelper.readString(con);
                LOG.info("Response was:", msg);
                throw new ActionParamsException("Problematic response from actual service");
            }

            // read the image tile
            final byte[] presponse = IOHelper.readBytes(con);
            final HttpServletResponse response = params.getResponse();
            response.setContentType(contentType);
            response.getOutputStream().write(presponse, 0, presponse.length);
            response.getOutputStream().flush();
            response.getOutputStream().close();
        } catch(ActionException e) {
            // just throw it as is if we already handled it
            throw e;
        } catch (Exception e) {
            throw new ActionParamsException("Couldn't proxy request to actual service", e.getMessage(), e);
        } finally {
            if(actionTimer != null) {
                actionTimer.stop();
            }
            if(con != null) {
                con.disconnect();
            }
        }
    }

    private boolean isContentTypeOK(String contentType) {
        return contentType.startsWith("image/")
                || contentType.startsWith("application/octet-stream")
                || contentType.startsWith("application/vnd.mapbox-vector-tile");
    }

    private String getURL(final ActionParameters params, final OskariLayer layer) throws ActionParamsException {
        if (params.getHttpParam(LEGEND, false)) {
            return this.getLegendURL(layer, params.getHttpParam(CapabilitiesConstants.KEY_STYLE, null));
        }
        final HttpServletRequest httpRequest = params.getRequest();
        if (OskariLayer.TYPE_WMTS.equalsIgnoreCase(layer.getType())) {
            // check for rest url
            final String urlTemplate = JSONHelper.getStringFromJSON(layer.getOptions(), "urlTemplate", null);
            if(urlTemplate != null) {
                LOG.debug("REST WMTS layer proxy");
                HashMap<String, String> capsParams = new HashMap<>();
                Enumeration<String> paramNames = httpRequest.getParameterNames();
                while (paramNames.hasMoreElements()) {
                    String paramName = paramNames.nextElement();
                    capsParams.put(paramName.toUpperCase(), params.getHttpParam(paramName));
                }
                return urlTemplate
                        .replaceFirst("\\{layer\\}", layer.getName())
                        .replaceFirst("\\{style\\}", capsParams.get(KEY_STYLE) != null ? capsParams.get(KEY_STYLE) : KEY_STYLE)
                        .replaceFirst("\\{TileMatrixSet\\}", capsParams.get(KEY_TILEMATRIXSET) != null ? capsParams.get(KEY_TILEMATRIXSET) : KEY_TILEMATRIXSET)
                        .replaceFirst("\\{TileMatrix\\}", capsParams.get(KEY_TILEMATRIX) != null ? capsParams.get(KEY_TILEMATRIX) : KEY_TILEMATRIX)
                        .replaceFirst("\\{TileRow\\}", capsParams.get(KEY_TILEROW) != null ? capsParams.get(KEY_TILEROW) : KEY_TILEROW)
                        .replaceFirst("\\{TileCol\\}", capsParams.get(KEY_TILECOL) != null ? capsParams.get(KEY_TILECOL) : KEY_TILECOL);
            }
        } else if (OskariLayer.TYPE_VECTOR_TILE.equalsIgnoreCase(layer.getType())) {
            // TODO: Figure out CRS
            int x = params.getRequiredParamInt(LayerJSONFormatterVectorTile.URL_PARAM_X);
            int y = params.getRequiredParamInt(LayerJSONFormatterVectorTile.URL_PARAM_Y);
            int z = params.getRequiredParamInt(LayerJSONFormatterVectorTile.URL_PARAM_Z);
            return layer.getUrl()
                    .replaceFirst("\\{x\\}", String.valueOf(x))
                    .replaceFirst("\\{y\\}", String.valueOf(y))
                    .replaceFirst("\\{z\\}", String.valueOf(z));
        }

        Map<String, String> urlParams = getUrlParams(httpRequest);
        return IOHelper.constructUrl(layer.getUrl(),urlParams);
    }

    private Map<String, String> getUrlParams(HttpServletRequest httpRequest) {
        Enumeration<String> paramNames = httpRequest.getParameterNames();
        Map<String, String> urlParams = new HashMap<>();
        // Refine parameters
        while (paramNames.hasMoreElements()){
            String paramName = paramNames.nextElement();
            if (!RESERVED_PARAMETERS.contains(paramName)) {
                urlParams.put(paramName, httpRequest.getParameter(paramName));
            }
        }
        return urlParams;
    }

    /**
     * Get Legend image url
     * @param layer  Oskari layer
     * @param styleName  style name for legend
     * @return
     */
    private String getLegendURL(final OskariLayer layer, String styleName) {
        // Get overridden legends
        Map<String,String> legends = JSONHelper.getObjectAsMap(layer.getOptions().optJSONObject(KEY_LEGENDS));
        String lurl = legends.getOrDefault(KEY_GLOBAL_LEGEND, "");
        if (styleName != null) {
            if (legends.containsKey(styleName)) {
                return legends.get(styleName);
            }
            if (!lurl.isEmpty()) {
                // use global legend url
                return lurl;
            }
            // Get Capabilities style url
            JSONObject json = layer.getCapabilities();
            if (json.has(CapabilitiesConstants.KEY_STYLES)) {

                JSONArray styles = JSONHelper.getJSONArray(json, CapabilitiesConstants.KEY_STYLES);
                for (int i = 0; i < styles.length(); i++) {
                    final JSONObject style = JSONHelper.getJSONObject(styles, i);
                    if (JSONHelper.getStringFromJSON(style, NAME, "").equals(styleName)) {
                        return style.optString(LEGEND);
                    }
                }

            }
        }
        return lurl;

    }
    /**
     * Creates connection
     * @param url URL (with params) to call
     * @param layer layer
     * @return connection
     * @throws ActionException
     */
    private HttpURLConnection getConnection(final String url, final OskariLayer layer)
            throws ActionException {
        try {
            final String username = layer.getUsername();
            final String password = layer.getPassword();
            LOG.debug("Getting layer tile from url:", url);
            return IOHelper.getConnection(url, username, password);
        } catch (Exception e) {
            throw new ActionException("Couldn't get connection to service", e);
        }
    }
}
