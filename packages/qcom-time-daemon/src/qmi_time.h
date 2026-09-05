/*
 * Qualcomm QMI TIME Service Definitions
 *
 * Reverse-engineered from Qualcomm time_daemon and Hexagon modem firmware (MSM8916)
 * Service ID: 0x16 (22 decimal)
 * Version: 1, Instance: 1
 */

#ifndef _QMI_TIME_H_
#define _QMI_TIME_H_

#include <stdint.h>
#include <stdbool.h>
#include <libqrtr.h>

#define QMI_TIME_SERVICE_ID		22
#define QMI_TIME_SERVICE_VERSION	1
#define QMI_TIME_SERVICE_INSTANCE	1

/* QMI TIME Message IDs */
#define QMI_TIME_GENOFF_SET_REQ		0x0020
#define QMI_TIME_GENOFF_GET_REQ		0x0021
#define QMI_TIME_SET_LEAP_SEC_REQ	0x0022
#define QMI_TIME_GET_LEAP_SEC_REQ	0x0023
#define QMI_TIME_TURN_OFF_IND_REQ	0x0024
#define QMI_TIME_REG_IND_REQ		0x0025
#define QMI_TIME_TOD_IND		0x0029
#define QMI_TIME_USER_IND		0x002d
#define QMI_TIME_SECURE_IND		0x002e

/* ATS Time Bases */
enum time_genoff_base {
	ATS_RTC		= 0,	/* Hardware Real-Time Clock base */
	ATS_TOD		= 1,	/* Time Of Day base (Network / NITZ / GPS sync) */
	ATS_USER	= 2,	/* User time base */
	ATS_SECURE	= 3,	/* Secure time base */
	ATS_DRM		= 4,	/* Digital Rights Management base */
	ATS_1X		= 5,	/* CDMA 1X base */
	ATS_HDR		= 6,	/* CDMA HDR / EVDO base */
	ATS_WCDMA	= 7,	/* WCDMA base */
	ATS_LTE		= 8,	/* LTE frame time base */
	ATS_GPS		= 9,	/* GPS time base */
	ATS_MAX		= 15,
};

/* Time units */
enum time_unit {
	TIME_UNIT_MSEC	= 0,	/* Milliseconds (default for GENOFF_SET) */
	TIME_UNIT_SEC	= 1,	/* Seconds */
	TIME_UNIT_USEC	= 2,	/* Microseconds */
};

/* Time operations */
enum time_genoff_opr {
	TIME_GENOFF_OP_SET	= 0,
	TIME_GENOFF_OP_GET	= 1,
};

/*
 * QMI_TIME_GENOFF_SET_REQ (0x0020)
 * Request: TLV 0x01 (4 bytes: base) + TLV 0x02 (8 bytes: offset)
 */
struct time_genoff_set_req {
	uint32_t base;		/* enum time_genoff_base (TLV 0x01) */
	uint64_t offset;	/* Milliseconds since Jan 6 1980 (GPS epoch) (TLV 0x02) */
};

/*
 * QMI_TIME_GENOFF_SET_RESP (0x0020)
 * Response: TLV 0x02 (4 bytes: result)
 */
struct time_genoff_set_resp {
	struct qmi_response_type_v01 result;
};

/*
 * QMI_TIME_GENOFF_GET_REQ (0x0021)
 * Request: TLV 0x01 (4 bytes: base)
 */
struct time_genoff_get_req {
	uint32_t base;		/* enum time_genoff_base (TLV 0x01) */
};

/*
 * QMI_TIME_GENOFF_GET_RESP (0x0021)
 * Response: TLV 0x02 (result) + TLV 0x03 (base) + TLV 0x04 (offset)
 */
struct time_genoff_get_resp {
	struct qmi_response_type_v01 result;
	uint32_t base;
	uint64_t offset;
};

/*
 * QMI_TIME_UPDATE_IND (0x0026 - 0x002F)
 * Indication: TLV 0x01 (4 bytes: base) + TLV 0x02 (8 bytes: offset)
 */
struct time_tod_ind {
	uint32_t base;
	uint64_t offset;
};

/* QMI Element Info structures for encoding/decoding */
extern struct qmi_elem_info time_qmi_result_ei[];
extern struct qmi_elem_info time_genoff_set_req_ei[];
extern struct qmi_elem_info time_genoff_set_resp_ei[];
extern struct qmi_elem_info time_genoff_get_req_ei[];
extern struct qmi_elem_info time_genoff_get_resp_ei[];
extern struct qmi_elem_info time_reg_ind_req_ei[];
extern struct qmi_elem_info time_reg_ind_resp_ei[];
extern struct qmi_elem_info time_tod_ind_ei[];

#endif /* _QMI_TIME_H_ */
