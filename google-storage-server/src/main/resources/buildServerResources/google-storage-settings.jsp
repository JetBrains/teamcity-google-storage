<%@ taglib prefix="props" tagdir="/WEB-INF/tags/props" %>
<%@ taglib prefix="l" tagdir="/WEB-INF/tags/layout" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="bs" tagdir="/WEB-INF/tags" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<jsp:useBean id="propertiesBean" scope="request" type="jetbrains.buildServer.controllers.BasePropertiesBean"/>
<jsp:useBean id="cons" class="jetbrains.buildServer.serverSide.artifacts.google.web.GoogleParametersProvider"/>

<style type="text/css">
    .runnerFormTable {
        margin-top: 1em;
    }
    .runnerFormTable td .posRel {
        padding-right: 0;
    }
</style>

<l:settingsGroup title="Security Credentials">
    <c:set var="credentialsValue" value="${propertiesBean.properties[cons.credentialsType]}"/>
    <c:set var="credentialsType" value="${empty credentialsValue ? cons.credentialsKey : credentialsValue}"/>
    <tr>
        <th><label for="${cons.credentialsType}">Credentials type: <l:star/></label></th>
        <td>
            <props:radioButtonProperty name="${cons.credentialsType}"
                                       id="${cons.credentialsEnvironment}"
                                       value="${cons.credentialsEnvironment}"
                                       checked="${credentialsType eq cons.credentialsEnvironment}"/>
            <label for="${cons.credentialsEnvironment}">From machine environment</label>
            <span class="smallNote">Use authentication from machine environment</span>
            <br/>
            <props:radioButtonProperty name="${cons.credentialsType}"
                                       id="${cons.credentialsKey}"
                                       value="${cons.credentialsKey}"
                                       checked="${credentialsType eq cons.credentialsKey}"/>
            <label for="${cons.credentialsKey}">JSON private key</label>
            <span class="smallNote">Specify private key for service account</span>
            <br/>
            <a href="https://console.cloud.google.com/iam-admin/" target="_blank">Open IAM Console</a>
        </td>
    </tr>
    <tr id="access-key-selector">
        <th class="noBorder"><label for="${cons.accessKey}">JSON private key: <l:star/></label></th>
        <td>
            <div class="posRel">
                <props:multilineProperty name="${cons.accessKey}"
                                         expanded="${fn:length(propertiesBean.properties[cons.accessKey]) == 0}"
                                         className="longField" note=""
                                         rows="5" cols="49" linkTitle="Edit JSON key"/>
            </div>
            <span class="smallNote">Specify the JSON private key.
                <bs:help urlPrefix="https://cloud.google.com/storage/docs/authentication#generating-a-private-key"
                         file=""/>
            </span>
            <div id="file-selector" class="hidden">
                <input type="file" />
            </div>
        </td>
    </tr>
    <tr>
        <td colspan="2">
            <span class="smallNote">
                You need to grant <em>Project Viewer</em> and <em>Storage Object Admin</em> roles or custom role with a following permissions: <em>storage.buckets.list</em>, <em>storage.objects.*</em>
                    <bs:help urlPrefix="https://cloud.google.com/storage/docs/access-control/iam#roles" file=""/>
            </span>
            <span class="error option-error" id="errors"></span>
        </td>
    </tr>
</l:settingsGroup>

<l:settingsGroup title="Storage Parameters">
    <tr class="advancedSetting">
        <th><label for="${cons.bucketName}">Bucket name:</label></th>
        <td>
            <div class="posRel">
                <div class="hidden" id="buckets-selector">
                    <c:set var="bucket" value="${propertiesBean.properties[cons.bucketName]}"/>
                    <props:selectProperty name="${cons.bucketName}" className="longField">
                        <c:if test="${not empty bucket}">
                            <props:option value="${bucket}"><c:out value="${bucket}"/></props:option>
                        </c:if>
                    </props:selectProperty>
                </div>
                <div class="longField inline-block" id="buckets-error">
                <span class="error option-error">
                    No buckets found. <a href="https://console.cloud.google.com/iam-admin/iam/project" target="_blank">Check permissions</a> or
                    <a href="https://console.cloud.google.com/storage/browser" target="_blank">create a new bucket</a>.
                </span>
                </div>
            </div>
            <i class="icon-refresh" title="Reload buckets" id="buckets-refresh"></i>
            <span class="smallNote">Specify the bucket name where artifacts will be published.</span>
        </td>
    </tr>
    <tr>
        <th class="noBorder">Options:</th>
        <td>
            <props:checkboxProperty name="${cons.useSignedUrlForUpload}"/>
            <label for="${cons.useSignedUrlForUpload}">Use <a href="https://cloud.google.com/storage/docs/access-control/signed-urls" target="_blank">signed URLs</a> for artifacts upload</label>
            <span class="smallNote">Prevents exposing security credentials to build agents.</span>
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
        var $refreshButton = $j('#buckets-refresh').addClass('icon-spin');
        $j.post(window['base_uri'] + '${cons.containersPath}', parameters)
            .then(function (response) {
                var $response = $j(response);
                var errors = getErrors($response);
                $j(BS.Util.escapeId('errors')).text(errors);
                $j(BS.Util.escapeId('buckets-error')).toggleClass('hidden', !errors);
                $j(BS.Util.escapeId('buckets-selector')).toggleClass('hidden', !!errors);

                if (errors) {
                    return;
                }

                var $selector = $j('#${cons.bucketName}');
                var value = $selector.val();
                $selector.empty();
                $response.find("buckets:eq(0) bucket").map(function () {
                    var text = $j(this).text();
                    $selector.append($j("<option></option>")
                        .attr("value", text).text(text));
                });
                if (value) {
                    $selector.val(value);
                }
            })
            .always(function () {
                $refreshButton.removeClass('icon-spin');
            });
    }

    var accessKeySelector = BS.Util.escapeId('${cons.accessKey}');
    $j(document).on('change', accessKeySelector, function () {
        loadBuckets();
    });
    $j(document).on('ready', function () {
        loadBuckets();
    });
    $j(document).on('click', '#buckets-refresh', function () {
        loadBuckets();
    });

    var credentialsSelector = 'input[type=radio][name="prop:${cons.credentialsType}"]';
    $j(document).on('change', credentialsSelector, function () {
        updateCredentialsVisibility();
    });

    function updateCredentialsVisibility() {
        var value = $j(credentialsSelector + ':checked').val();
        $j('#access-key-selector').toggle(value === '${cons.credentialsKey}');

        BS.MultilineProperties.updateVisible();

        loadBuckets();
    }

    if (typeof FileReader !== "undefined") {
        var $textArea = $j(accessKeySelector);
        var loadAccessKey = function (file) {
            var reader = new FileReader();
            reader.onload = function (e) {
                $textArea.val(e.target.result);
                loadBuckets()
            };
            reader.readAsText(file);
        };

        $textArea.on('dragover', false).on('drop', function (e) {
            var dt = e.dataTransfer || e.originalEvent.dataTransfer;
            if (dt.items) {
                for (var i = 0; i < dt.items.length; i++) {
                    if (dt.items[i].kind === "file") {
                        var file = dt.items[i].getAsFile();
                        loadAccessKey(file);
                        return false
                    }
                }
            } else {
                for (var i = 0; i < dt.files.length; i++) {
                    loadAccessKey(dt.files[i]);
                    return false
                }
            }
        });

        $j('#file-selector').removeClass('hidden')
            .find('input[type=file]').on('change', function () {
                loadAccessKey(this.files[0]);
            });
    }

    updateCredentialsVisibility();
</script>