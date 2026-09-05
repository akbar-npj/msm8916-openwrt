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
