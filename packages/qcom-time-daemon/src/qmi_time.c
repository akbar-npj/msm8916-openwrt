/*
 * Qualcomm QMI TIME Service TLV Element Info Definitions
 *
 * Implements QMI message encoding/decoding descriptors for libqrtr
 */

#include <stddef.h>
#include <stdint.h>
#include <stdbool.h>
#include "qmi_time.h"

struct qmi_elem_info time_qmi_result_ei[] = {
	{
		.data_type = QMI_UNSIGNED_2_BYTE,
		.elem_len = 1,
		.elem_size = sizeof(uint16_t),
		.offset = offsetof(struct qmi_response_type_v01, result),
	},
	{
		.data_type = QMI_UNSIGNED_2_BYTE,
		.elem_len = 1,
		.elem_size = sizeof(uint16_t),
		.offset = offsetof(struct qmi_response_type_v01, error),
	},
	{}
};

struct qmi_elem_info time_genoff_set_req_ei[] = {
	{
		.data_type = QMI_UNSIGNED_4_BYTE,
		.elem_len = 1,
		.elem_size = sizeof(uint32_t),
		.tlv_type = 0x01,
		.offset = offsetof(struct time_genoff_set_req, base),
	},
	{
		.data_type = QMI_UNSIGNED_8_BYTE,
		.elem_len = 1,
		.elem_size = sizeof(uint64_t),
		.tlv_type = 0x02,
		.offset = offsetof(struct time_genoff_set_req, offset),
	},
	{}
};

struct qmi_elem_info time_genoff_set_resp_ei[] = {
	{
		.data_type = QMI_STRUCT,
		.elem_len = 1,
		.elem_size = sizeof(struct qmi_response_type_v01),
		.tlv_type = 0x02,
		.offset = offsetof(struct time_genoff_set_resp, result),
		.ei_array = time_qmi_result_ei,
	},
	{}
};

struct qmi_elem_info time_genoff_get_req_ei[] = {
	{
		.data_type = QMI_UNSIGNED_4_BYTE,
		.elem_len = 1,
		.elem_size = sizeof(uint32_t),
		.tlv_type = 0x01,
		.offset = offsetof(struct time_genoff_get_req, base),
	},
	{}
};

struct qmi_elem_info time_genoff_get_resp_ei[] = {
	{
		.data_type = QMI_STRUCT,
		.elem_len = 1,
		.elem_size = sizeof(struct qmi_response_type_v01),
		.tlv_type = 0x02,
		.offset = offsetof(struct time_genoff_get_resp, result),
		.ei_array = time_qmi_result_ei,
	},
	{
		.data_type = QMI_UNSIGNED_4_BYTE,
		.elem_len = 1,
		.elem_size = sizeof(uint32_t),
		.tlv_type = 0x03,
		.offset = offsetof(struct time_genoff_get_resp, base),
	},
	{
		.data_type = QMI_UNSIGNED_8_BYTE,
		.elem_len = 1,
		.elem_size = sizeof(uint64_t),
		.tlv_type = 0x04,
		.offset = offsetof(struct time_genoff_get_resp, offset),
	},
	{}
};

struct qmi_elem_info time_tod_ind_ei[] = {
	{
		.data_type = QMI_UNSIGNED_4_BYTE,
		.elem_len = 1,
		.elem_size = sizeof(uint32_t),
		.tlv_type = 0x01,
		.offset = offsetof(struct time_tod_ind, base),
	},
	{
		.data_type = QMI_UNSIGNED_8_BYTE,
		.elem_len = 1,
		.elem_size = sizeof(uint64_t),
		.tlv_type = 0x02,
		.offset = offsetof(struct time_tod_ind, offset),
	},
	{}
};

const char *qmi_time_msg_name(uint16_t msg_id)
{
	switch (msg_id) {
	case QMI_TIME_GENOFF_SET_REQ:		return "GENOFF_SET";
	case QMI_TIME_GENOFF_GET_REQ:		return "GENOFF_GET";
	case QMI_TIME_TURN_OFF_IND_REQ:		return "TURN_OFF_IND";
	case QMI_TIME_TURN_ON_IND_REQ:		return "TURN_ON_IND";
	case QMI_TIME_LEAP_SEC_SET_REQ:		return "LEAP_SEC_SET";
	case QMI_TIME_LEAP_SEC_GET_REQ:		return "LEAP_SEC_GET";
	case QMI_TIME_ATS_RTC_UPDATE_IND:	return "ATS_RTC_UPDATE_IND";
	case QMI_TIME_ATS_TOD_UPDATE_IND:	return "ATS_TOD_UPDATE_IND";
	case QMI_TIME_ATS_USER_UPDATE_IND:	return "ATS_USER_UPDATE_IND";
	case QMI_TIME_ATS_SECURE_UPDATE_IND:	return "ATS_SECURE_UPDATE_IND";
	case QMI_TIME_ATS_DRM_UPDATE_IND:	return "ATS_DRM_UPDATE_IND";
	case QMI_TIME_ATS_USER_UTC_UPDATE_IND:	return "ATS_USER_UTC_UPDATE_IND";
	case QMI_TIME_ATS_USER_TZ_DL_UPDATE_IND:return "ATS_USER_TZ_DL_UPDATE_IND";
	case QMI_TIME_ATS_GPS_UPDATE_IND:	return "ATS_GPS_UPDATE_IND";
	case QMI_TIME_ATS_1X_UPDATE_IND:	return "ATS_1X_UPDATE_IND";
	case QMI_TIME_ATS_HDR_UPDATE_IND:	return "ATS_HDR_UPDATE_IND";
	case QMI_TIME_ATS_WCDMA_UPDATE_IND:	return "ATS_WCDMA_UPDATE_IND";
	case QMI_TIME_ATS_BREW_UPDATE_IND:	return "ATS_BREW_UPDATE_IND";
	default:				return "UNKNOWN";
	}
}

const char *qmi_time_base_name(uint32_t base)
{
	switch (base) {
	case ATS_RTC:		return "ATS_RTC";
	case ATS_TOD:		return "ATS_TOD";
	case ATS_USER:		return "ATS_USER";
	case ATS_SECURE:	return "ATS_SECURE";
	case ATS_DRM:		return "ATS_DRM";
	case ATS_1X:		return "ATS_1X";
	case ATS_HDR:		return "ATS_HDR";
	case ATS_WCDMA:		return "ATS_WCDMA";
	case ATS_LTE:		return "ATS_LTE";
	case ATS_GPS:		return "ATS_GPS";
	default:		return "ATS_OTHER";
	}
}

