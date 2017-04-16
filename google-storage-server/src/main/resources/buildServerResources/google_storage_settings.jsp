<%@ taglib prefix="props" tagdir="/WEB-INF/tags/props" %>
<%@ taglib prefix="l" tagdir="/WEB-INF/tags/layout" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="bs" tagdir="/WEB-INF/tags" %>
<jsp:useBean id="propertiesBean" scope="request" type="jetbrains.buildServer.controllers.BasePropertiesBean"/>
<jsp:useBean id="params" class="jetbrains.buildServer.serverSide.artifacts.google.web.GoogleParametersProvider"/>

<style type="text/css">
    .runnerFormTable {
        margin-top: 1em;
    }
</style>

<l:settingsGroup title="Storage Credentials">
    <tr>
        <th class="noBorder"><label for="${params.accessKey}">JSON private key: <l:star/></label></th>
        <td>
            <div class="posRel">
                <props:multilineProperty expanded="true" name="${params.accessKey}"
                                         className="longField" note=""
                                         rows="5" cols="49" linkTitle="Edit JSON key"/>
            </div>
            <span class="error" id="error_${params.accessKey}"></span>
            <span class="smallNote">Specify the JSON private key.
            <bs:help urlPrefix="https://cloud.google.com/storage/docs/authentication#generating-a-private-key"
                     file=""/>
            </span>
        </td>
    </tr>
</l:settingsGroup>

<l:settingsGroup title="Storage Parameters">
    <tr class="advancedSetting">
        <th class="noBorder"><label for="${params.bucketName}">Bucket name:</label></th>
        <td>
            <div class="posRel">
                <c:set var="bucket" value="${propertiesBean.properties[params.bucketName]}"/>
                <props:selectProperty name="${params.bucketName}" className="longField">
                    <c:if test="${not empty bucket}">
                        <props:option value="${bucket}"><c:out value="${bucket}"/></props:option>
                    </c:if>
                </props:selectProperty>
            </div>
            <span class="smallNote">Specify the bucket name where artifacts will be published.<br/>
                You can override default path prefix via build parameter <em><c:out value="${params.pathPrefix}"/></em>
            </span>
        </td>
    </tr>
</l:settingsGroup>

<script type="text/javascript">
    function getErrors($response) {
        var $errors = $response.find("errors:eq(0) error");
        if ($errors.length) {
            return $errors.text();
        }

        return "";
    }

    function loadBuckets() {
        var parameters = BS.EditStorageForm.serializeParameters();
        $j.post(window['base_uri'] + '${params.containersPath}', parameters)
            .then(function (response) {
                var $response = $j(response);
                var errors = getErrors($response);
                $j(BS.Util.escapeId('error_${params.accessKey}')).text(errors);

                if (errors) {
                    return;
                }

                var $selector = $j('#${params.bucketName}');
                var value = $selector.val();
                $selector.empty();
                $response.find("buckets:eq(0) bucket").map(function () {
                    var text = $j(this).text();
                    $selector.append($j("<option></option>")
                        .attr("value", text).text(text));
                });
                $selector.val(value);
            });
    }
    var selectors = BS.Util.escapeId('${params.accessKey}');
    $j(document).on('change', selectors, function () {
        loadBuckets();
    });
    $j(document).on('ready', function () {
        loadBuckets();
    });
</script>