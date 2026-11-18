/* SPDX-License-Identifier: BSD-3-Clause */
#include <libqmi-glib.h>
#include <stdio.h>
#include <string.h>

static GMainLoop *loop;
static QmiDevice *device;
static QmiClientWds *client;
static int requested_pco = -1;
static gboolean verifying;
static int status = 1;

static void released(GObject *object, GAsyncResult *result, gpointer data G_GNUC_UNUSED)
{
    g_autoptr(GError) error = NULL;
    if (!qmi_device_release_client_finish(QMI_DEVICE(object), result, &error)) {
        fprintf(stderr, "Could not release WDS client: %s\n", error->message);
        status = 1;
    }
    g_main_loop_quit(loop);
}

static void finish(void)
{
    if (client)
        qmi_device_release_client(device, QMI_CLIENT(client), QMI_DEVICE_RELEASE_CLIENT_FLAGS_RELEASE_CID,
                                  5, NULL, released, NULL);
    else
        g_main_loop_quit(loop);
}

static void fail(GError *error)
{
    fprintf(stderr, "%s\n", error ? error->message : "QMI operation failed");
    g_clear_error(&error);
    status = 1;
    finish();
}

static void write_profile(void);

static void read_done(GObject *object G_GNUC_UNUSED, GAsyncResult *result, gpointer data G_GNUC_UNUSED)
{
    GError *error = NULL;
    gboolean flag = FALSE;
    const gchar *apn = NULL;
    QmiWdsPdpType pdp;
    g_autoptr(QmiMessageWdsGetProfileSettingsOutput) output =
        qmi_client_wds_get_profile_settings_finish(client, result, &error);
    if (!output || !qmi_message_wds_get_profile_settings_output_get_result(output, &error) ||
        !qmi_message_wds_get_profile_settings_output_get_apn_name(output, &apn, &error) ||
        !qmi_message_wds_get_profile_settings_output_get_pdp_type(output, &pdp, &error)) {
        fail(error);
        return;
    }
    /* Profile 2 may be in use for an unrelated APN on another modem. */
    if (g_strcmp0(apn, "ims") || pdp != QMI_WDS_PDP_TYPE_IPV4) {
        fail(g_error_new_literal(G_IO_ERROR, G_IO_ERROR_FAILED, "Profile 2 is not ims/IPv4"));
        return;
    }
    gboolean present = qmi_message_wds_get_profile_settings_output_get_pcscf_address_using_pco(output, &flag, NULL);
    printf("3GPP profile 2 APN=%s PDP=IPv4 P-CSCF-over-PCO=%s\n", apn,
           present ? (flag ? "enabled" : "disabled") : "omitted");
    if (requested_pco >= 0 && !verifying && (!present || flag != requested_pco)) {
        write_profile();
        return;
    }
    status = 0;
    if (requested_pco >= 0 && (!present || flag != requested_pco)) {
        fprintf(stderr, "Profile 2 did not retain the requested P-CSCF setting\n");
        status = 1;
    }
    finish();
}

static void read_profile(void)
{
    g_autoptr(QmiMessageWdsGetProfileSettingsInput) input = qmi_message_wds_get_profile_settings_input_new();
    qmi_message_wds_get_profile_settings_input_set_profile_id(input, QMI_WDS_PROFILE_TYPE_3GPP, 2, NULL);
    qmi_client_wds_get_profile_settings(client, input, 8, NULL, read_done, NULL);
}

static void write_done(GObject *object G_GNUC_UNUSED, GAsyncResult *result, gpointer data G_GNUC_UNUSED)
{
    GError *error = NULL;
    g_autoptr(QmiMessageWdsModifyProfileOutput) output =
        qmi_client_wds_modify_profile_finish(client, result, &error);
    if (!output || !qmi_message_wds_modify_profile_output_get_result(output, &error)) {
        fail(error);
        return;
    }
    verifying = TRUE;
    read_profile();
}

static void write_profile(void)
{
    g_autoptr(QmiMessageWdsModifyProfileInput) input = qmi_message_wds_modify_profile_input_new();
    qmi_message_wds_modify_profile_input_set_profile_identifier(input, QMI_WDS_PROFILE_TYPE_3GPP, 2, NULL);
    qmi_message_wds_modify_profile_input_set_pcscf_address_using_pco(input, requested_pco, NULL);
    qmi_client_wds_modify_profile(client, input, 8, NULL, write_done, NULL);
}

static void allocated(GObject *object G_GNUC_UNUSED, GAsyncResult *result, gpointer data G_GNUC_UNUSED)
{
    GError *error = NULL;
    client = (QmiClientWds *)qmi_device_allocate_client_finish(device, result, &error);
    if (!client) {
        fail(error);
        return;
    }
    read_profile();
}

static void opened(GObject *object G_GNUC_UNUSED, GAsyncResult *result, gpointer data G_GNUC_UNUSED)
{
    GError *error = NULL;
    if (!qmi_device_open_finish(device, result, &error)) {
        fail(error);
        return;
    }
    qmi_device_allocate_client(device, QMI_SERVICE_WDS, QMI_CID_NONE, 8, NULL, allocated, NULL);
}

static void created(GObject *object G_GNUC_UNUSED, GAsyncResult *result, gpointer data G_GNUC_UNUSED)
{
    GError *error = NULL;
    device = qmi_device_new_finish(result, &error);
    if (!device) {
        fail(error);
        return;
    }
    qmi_device_open(device, QMI_DEVICE_OPEN_FLAGS_PROXY, 8, NULL, opened, NULL);
}

int main(int argc, char **argv)
{
    if (argc != 3 || (strcmp(argv[2], "status") && strcmp(argv[2], "enable") &&
                      strcmp(argv[2], "disable"))) {
        fprintf(stderr, "Usage: %s QMI_DEVICE status|enable|disable\n", argv[0]);
        return 2;
    }
    if (strcmp(argv[2], "status"))
        requested_pco = !strcmp(argv[2], "enable");
    loop = g_main_loop_new(NULL, FALSE);
    g_autoptr(GFile) file = g_file_new_for_path(argv[1]);
    qmi_device_new(file, NULL, created, NULL);
    g_main_loop_run(loop);
    g_clear_object(&client);
    g_clear_object(&device);
    g_main_loop_unref(loop);
    return status;
}
