package com.android.phone;

import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.util.Log;
import com.android.internal.telephony.Phone;

/* JADX INFO: loaded from: classes.dex */
public class CellBroadcastSms extends PreferenceActivity implements Preference.OnPreferenceChangeListener {
    private CheckBoxPreference mButtonAdministrative;
    private CheckBoxPreference mButtonAdvertisements;
    private CheckBoxPreference mButtonAtr;
    private CheckBoxPreference mButtonBcSms;
    private CheckBoxPreference mButtonEmergencyBroadcast;
    private CheckBoxPreference mButtonEo;
    private CheckBoxPreference mButtonInternational1;
    private CheckBoxPreference mButtonInternational2;
    private CheckBoxPreference mButtonInternational3;
    private CheckBoxPreference mButtonInternational4;
    private CheckBoxPreference mButtonLafs;
    private CheckBoxPreference mButtonLocal1;
    private CheckBoxPreference mButtonLocal2;
    private CheckBoxPreference mButtonLocal3;
    private CheckBoxPreference mButtonLocal4;
    private CheckBoxPreference mButtonLocalWeather;
    private CheckBoxPreference mButtonLodgings;
    private CheckBoxPreference mButtonMaintenance;
    private CheckBoxPreference mButtonMhh;
    private CheckBoxPreference mButtonMultiCategory;
    private CheckBoxPreference mButtonNational1;
    private CheckBoxPreference mButtonNational2;
    private CheckBoxPreference mButtonNational3;
    private CheckBoxPreference mButtonNational4;
    private CheckBoxPreference mButtonRegional1;
    private CheckBoxPreference mButtonRegional2;
    private CheckBoxPreference mButtonRegional3;
    private CheckBoxPreference mButtonRegional4;
    private CheckBoxPreference mButtonRestaurants;
    private CheckBoxPreference mButtonRetailDirectory;
    private CheckBoxPreference mButtonStockQuotes;
    private CheckBoxPreference mButtonTechnologyNews;
    private MyHandler mHandler;
    private ListPreference mListLanguage;
    private Phone mPhone;

    @Override // android.preference.PreferenceActivity
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if (preference == this.mButtonBcSms) {
            if (this.mButtonBcSms.isChecked()) {
                this.mPhone.activateCellBroadcastSms(0, Message.obtain(this.mHandler, 1));
                Settings.Global.putInt(this.mPhone.getContext().getContentResolver(), "cdma_cell_broadcast_sms", 0);
                enableDisableAllCbConfigButtons(true);
            } else {
                this.mPhone.activateCellBroadcastSms(1, Message.obtain(this.mHandler, 1));
                Settings.Global.putInt(this.mPhone.getContext().getContentResolver(), "cdma_cell_broadcast_sms", 1);
                enableDisableAllCbConfigButtons(false);
            }
        } else if (preference != this.mListLanguage) {
            if (preference != this.mButtonEmergencyBroadcast) {
                if (preference != this.mButtonAdministrative) {
                    if (preference != this.mButtonMaintenance) {
                        if (preference != this.mButtonLocalWeather) {
                            if (preference != this.mButtonAtr) {
                                if (preference != this.mButtonLafs) {
                                    if (preference != this.mButtonRestaurants) {
                                        if (preference != this.mButtonLodgings) {
                                            if (preference != this.mButtonRetailDirectory) {
                                                if (preference != this.mButtonAdvertisements) {
                                                    if (preference != this.mButtonStockQuotes) {
                                                        if (preference != this.mButtonEo) {
                                                            if (preference != this.mButtonMhh) {
                                                                if (preference != this.mButtonTechnologyNews) {
                                                                    if (preference != this.mButtonMultiCategory) {
                                                                        if (preference != this.mButtonLocal1) {
                                                                            if (preference != this.mButtonRegional1) {
                                                                                if (preference != this.mButtonNational1) {
                                                                                    if (preference != this.mButtonInternational1) {
                                                                                        if (preference != this.mButtonLocal2) {
                                                                                            if (preference != this.mButtonRegional2) {
                                                                                                if (preference != this.mButtonNational2) {
                                                                                                    if (preference != this.mButtonInternational2) {
                                                                                                        if (preference != this.mButtonLocal3) {
                                                                                                            if (preference != this.mButtonRegional3) {
                                                                                                                if (preference != this.mButtonNational3) {
                                                                                                                    if (preference != this.mButtonInternational3) {
                                                                                                                        if (preference != this.mButtonLocal4) {
                                                                                                                            if (preference != this.mButtonRegional4) {
                                                                                                                                if (preference != this.mButtonNational4) {
                                                                                                                                    if (preference != this.mButtonInternational4) {
                                                                                                                                        preferenceScreen.setEnabled(false);
                                                                                                                                        return false;
                                                                                                                                    }
                                                                                                                                    CellBroadcastSmsConfig.setConfigDataCompleteBSelected(this.mButtonInternational4.isChecked(), 19);
                                                                                                                                    CellBroadcastSmsConfig.setCbSmsBSelectedValue(this.mButtonInternational4.isChecked(), 19);
                                                                                                                                } else {
                                                                                                                                    CellBroadcastSmsConfig.setConfigDataCompleteBSelected(this.mButtonNational4.isChecked(), 18);
                                                                                                                                    CellBroadcastSmsConfig.setCbSmsBSelectedValue(this.mButtonNational4.isChecked(), 18);
                                                                                                                                }
                                                                                                                            } else {
                                                                                                                                CellBroadcastSmsConfig.setConfigDataCompleteBSelected(this.mButtonRegional4.isChecked(), 17);
                                                                                                                                CellBroadcastSmsConfig.setCbSmsBSelectedValue(this.mButtonRegional4.isChecked(), 17);
                                                                                                                            }
                                                                                                                        } else {
                                                                                                                            CellBroadcastSmsConfig.setConfigDataCompleteBSelected(this.mButtonLocal4.isChecked(), 16);
                                                                                                                            CellBroadcastSmsConfig.setCbSmsBSelectedValue(this.mButtonLocal4.isChecked(), 16);
                                                                                                                        }
                                                                                                                    } else {
                                                                                                                        CellBroadcastSmsConfig.setConfigDataCompleteBSelected(this.mButtonInternational3.isChecked(), 15);
                                                                                                                        CellBroadcastSmsConfig.setCbSmsBSelectedValue(this.mButtonInternational3.isChecked(), 15);
                                                                                                                    }
                                                                                                                } else {
                                                                                                                    CellBroadcastSmsConfig.setConfigDataCompleteBSelected(this.mButtonNational3.isChecked(), 14);
                                                                                                                    CellBroadcastSmsConfig.setCbSmsBSelectedValue(this.mButtonNational3.isChecked(), 14);
                                                                                                                }
                                                                                                            } else {
                                                                                                                CellBroadcastSmsConfig.setConfigDataCompleteBSelected(this.mButtonRegional3.isChecked(), 13);
                                                                                                                CellBroadcastSmsConfig.setCbSmsBSelectedValue(this.mButtonRegional3.isChecked(), 13);
                                                                                                            }
                                                                                                        } else {
                                                                                                            CellBroadcastSmsConfig.setConfigDataCompleteBSelected(this.mButtonLocal3.isChecked(), 12);
                                                                                                            CellBroadcastSmsConfig.setCbSmsBSelectedValue(this.mButtonLocal3.isChecked(), 12);
                                                                                                        }
                                                                                                    } else {
                                                                                                        CellBroadcastSmsConfig.setConfigDataCompleteBSelected(this.mButtonInternational2.isChecked(), 11);
                                                                                                        CellBroadcastSmsConfig.setCbSmsBSelectedValue(this.mButtonInternational2.isChecked(), 11);
                                                                                                    }
                                                                                                } else {
                                                                                                    CellBroadcastSmsConfig.setConfigDataCompleteBSelected(this.mButtonNational2.isChecked(), 10);
                                                                                                    CellBroadcastSmsConfig.setCbSmsBSelectedValue(this.mButtonNational2.isChecked(), 10);
                                                                                                }
                                                                                            } else {
                                                                                                CellBroadcastSmsConfig.setConfigDataCompleteBSelected(this.mButtonRegional2.isChecked(), 9);
                                                                                                CellBroadcastSmsConfig.setCbSmsBSelectedValue(this.mButtonRegional2.isChecked(), 9);
                                                                                            }
                                                                                        } else {
                                                                                            CellBroadcastSmsConfig.setConfigDataCompleteBSelected(this.mButtonLocal2.isChecked(), 8);
                                                                                            CellBroadcastSmsConfig.setCbSmsBSelectedValue(this.mButtonLocal2.isChecked(), 8);
                                                                                        }
                                                                                    } else {
                                                                                        CellBroadcastSmsConfig.setConfigDataCompleteBSelected(this.mButtonInternational1.isChecked(), 7);
                                                                                        CellBroadcastSmsConfig.setCbSmsBSelectedValue(this.mButtonInternational1.isChecked(), 7);
                                                                                    }
                                                                                } else {
                                                                                    CellBroadcastSmsConfig.setConfigDataCompleteBSelected(this.mButtonNational1.isChecked(), 6);
                                                                                    CellBroadcastSmsConfig.setCbSmsBSelectedValue(this.mButtonNational1.isChecked(), 6);
                                                                                }
                                                                            } else {
                                                                                CellBroadcastSmsConfig.setConfigDataCompleteBSelected(this.mButtonRegional1.isChecked(), 5);
                                                                                CellBroadcastSmsConfig.setCbSmsBSelectedValue(this.mButtonRegional1.isChecked(), 5);
                                                                            }
                                                                        } else {
                                                                            CellBroadcastSmsConfig.setConfigDataCompleteBSelected(this.mButtonLocal1.isChecked(), 4);
                                                                            CellBroadcastSmsConfig.setCbSmsBSelectedValue(this.mButtonLocal1.isChecked(), 4);
                                                                        }
                                                                    } else {
                                                                        CellBroadcastSmsConfig.setConfigDataCompleteBSelected(this.mButtonMultiCategory.isChecked(), 31);
                                                                        CellBroadcastSmsConfig.setCbSmsBSelectedValue(this.mButtonMultiCategory.isChecked(), 31);
                                                                    }
                                                                } else {
                                                                    CellBroadcastSmsConfig.setConfigDataCompleteBSelected(this.mButtonTechnologyNews.isChecked(), 30);
                                                                    CellBroadcastSmsConfig.setCbSmsBSelectedValue(this.mButtonTechnologyNews.isChecked(), 30);
                                                                }
                                                            } else {
                                                                CellBroadcastSmsConfig.setConfigDataCompleteBSelected(this.mButtonMhh.isChecked(), 29);
                                                                CellBroadcastSmsConfig.setCbSmsBSelectedValue(this.mButtonMhh.isChecked(), 29);
                                                            }
                                                        } else {
                                                            CellBroadcastSmsConfig.setConfigDataCompleteBSelected(this.mButtonEo.isChecked(), 28);
                                                            CellBroadcastSmsConfig.setCbSmsBSelectedValue(this.mButtonEo.isChecked(), 28);
                                                        }
                                                    } else {
                                                        CellBroadcastSmsConfig.setConfigDataCompleteBSelected(this.mButtonStockQuotes.isChecked(), 27);
                                                        CellBroadcastSmsConfig.setCbSmsBSelectedValue(this.mButtonStockQuotes.isChecked(), 27);
                                                    }
                                                } else {
                                                    CellBroadcastSmsConfig.setConfigDataCompleteBSelected(this.mButtonAdvertisements.isChecked(), 26);
                                                    CellBroadcastSmsConfig.setCbSmsBSelectedValue(this.mButtonAdvertisements.isChecked(), 26);
                                                }
                                            } else {
                                                CellBroadcastSmsConfig.setConfigDataCompleteBSelected(this.mButtonRetailDirectory.isChecked(), 25);
                                                CellBroadcastSmsConfig.setCbSmsBSelectedValue(this.mButtonRetailDirectory.isChecked(), 25);
                                            }
                                        } else {
                                            CellBroadcastSmsConfig.setConfigDataCompleteBSelected(this.mButtonLodgings.isChecked(), 24);
                                            CellBroadcastSmsConfig.setCbSmsBSelectedValue(this.mButtonLodgings.isChecked(), 24);
                                        }
                                    } else {
                                        CellBroadcastSmsConfig.setConfigDataCompleteBSelected(this.mButtonRestaurants.isChecked(), 23);
                                        CellBroadcastSmsConfig.setCbSmsBSelectedValue(this.mButtonRestaurants.isChecked(), 23);
                                    }
                                } else {
                                    CellBroadcastSmsConfig.setConfigDataCompleteBSelected(this.mButtonLafs.isChecked(), 22);
                                    CellBroadcastSmsConfig.setCbSmsBSelectedValue(this.mButtonLafs.isChecked(), 22);
                                }
                            } else {
                                CellBroadcastSmsConfig.setConfigDataCompleteBSelected(this.mButtonAtr.isChecked(), 21);
                                CellBroadcastSmsConfig.setCbSmsBSelectedValue(this.mButtonAtr.isChecked(), 21);
                            }
                        } else {
                            CellBroadcastSmsConfig.setConfigDataCompleteBSelected(this.mButtonLocalWeather.isChecked(), 20);
                            CellBroadcastSmsConfig.setCbSmsBSelectedValue(this.mButtonLocalWeather.isChecked(), 20);
                        }
                    } else {
                        CellBroadcastSmsConfig.setConfigDataCompleteBSelected(this.mButtonMaintenance.isChecked(), 3);
                        CellBroadcastSmsConfig.setCbSmsBSelectedValue(this.mButtonMaintenance.isChecked(), 3);
                    }
                } else {
                    CellBroadcastSmsConfig.setConfigDataCompleteBSelected(this.mButtonAdministrative.isChecked(), 2);
                    CellBroadcastSmsConfig.setCbSmsBSelectedValue(this.mButtonAdministrative.isChecked(), 2);
                }
            } else {
                CellBroadcastSmsConfig.setConfigDataCompleteBSelected(this.mButtonEmergencyBroadcast.isChecked(), 1);
                CellBroadcastSmsConfig.setCbSmsBSelectedValue(this.mButtonEmergencyBroadcast.isChecked(), 1);
            }
        }
        return true;
    }

    @Override // android.preference.Preference.OnPreferenceChangeListener
    public boolean onPreferenceChange(Preference preference, Object objValue) {
        if (preference != this.mListLanguage) {
            return true;
        }
        CellBroadcastSmsConfig.setConfigDataCompleteLanguage(this.mListLanguage.findIndexOfValue((String) objValue) + 1);
        return true;
    }

    @Override // android.preference.PreferenceActivity, android.app.Activity
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.cell_broadcast_sms);
        this.mPhone = PhoneGlobals.getPhone();
        this.mHandler = new MyHandler();
        PreferenceScreen prefSet = getPreferenceScreen();
        this.mButtonBcSms = (CheckBoxPreference) prefSet.findPreference("button_enable_disable_cell_bc_sms");
        this.mListLanguage = (ListPreference) prefSet.findPreference("list_language");
        this.mListLanguage.setOnPreferenceChangeListener(this);
        this.mButtonEmergencyBroadcast = (CheckBoxPreference) prefSet.findPreference("button_emergency_broadcast");
        this.mButtonAdministrative = (CheckBoxPreference) prefSet.findPreference("button_administrative");
        this.mButtonMaintenance = (CheckBoxPreference) prefSet.findPreference("button_maintenance");
        this.mButtonLocalWeather = (CheckBoxPreference) prefSet.findPreference("button_local_weather");
        this.mButtonAtr = (CheckBoxPreference) prefSet.findPreference("button_atr");
        this.mButtonLafs = (CheckBoxPreference) prefSet.findPreference("button_lafs");
        this.mButtonRestaurants = (CheckBoxPreference) prefSet.findPreference("button_restaurants");
        this.mButtonLodgings = (CheckBoxPreference) prefSet.findPreference("button_lodgings");
        this.mButtonRetailDirectory = (CheckBoxPreference) prefSet.findPreference("button_retail_directory");
        this.mButtonAdvertisements = (CheckBoxPreference) prefSet.findPreference("button_advertisements");
        this.mButtonStockQuotes = (CheckBoxPreference) prefSet.findPreference("button_stock_quotes");
        this.mButtonEo = (CheckBoxPreference) prefSet.findPreference("button_eo");
        this.mButtonMhh = (CheckBoxPreference) prefSet.findPreference("button_mhh");
        this.mButtonTechnologyNews = (CheckBoxPreference) prefSet.findPreference("button_technology_news");
        this.mButtonMultiCategory = (CheckBoxPreference) prefSet.findPreference("button_multi_category");
        this.mButtonLocal1 = (CheckBoxPreference) prefSet.findPreference("button_local_general_news");
        this.mButtonRegional1 = (CheckBoxPreference) prefSet.findPreference("button_regional_general_news");
        this.mButtonNational1 = (CheckBoxPreference) prefSet.findPreference("button_national_general_news");
        this.mButtonInternational1 = (CheckBoxPreference) prefSet.findPreference("button_international_general_news");
        this.mButtonLocal2 = (CheckBoxPreference) prefSet.findPreference("button_local_bf_news");
        this.mButtonRegional2 = (CheckBoxPreference) prefSet.findPreference("button_regional_bf_news");
        this.mButtonNational2 = (CheckBoxPreference) prefSet.findPreference("button_national_bf_news");
        this.mButtonInternational2 = (CheckBoxPreference) prefSet.findPreference("button_international_bf_news");
        this.mButtonLocal3 = (CheckBoxPreference) prefSet.findPreference("button_local_sports_news");
        this.mButtonRegional3 = (CheckBoxPreference) prefSet.findPreference("button_regional_sports_news");
        this.mButtonNational3 = (CheckBoxPreference) prefSet.findPreference("button_national_sports_news");
        this.mButtonInternational3 = (CheckBoxPreference) prefSet.findPreference("button_international_sports_news");
        this.mButtonLocal4 = (CheckBoxPreference) prefSet.findPreference("button_local_entertainment_news");
        this.mButtonRegional4 = (CheckBoxPreference) prefSet.findPreference("button_regional_entertainment_news");
        this.mButtonNational4 = (CheckBoxPreference) prefSet.findPreference("button_national_entertainment_news");
        this.mButtonInternational4 = (CheckBoxPreference) prefSet.findPreference("button_international_entertainment_news");
    }

    @Override // android.app.Activity
    protected void onResume() {
        super.onResume();
        getPreferenceScreen().setEnabled(true);
        int settingCbSms = Settings.Global.getInt(this.mPhone.getContext().getContentResolver(), "cdma_cell_broadcast_sms", 1);
        this.mButtonBcSms.setChecked(settingCbSms == 0);
        if (this.mButtonBcSms.isChecked()) {
            enableDisableAllCbConfigButtons(true);
        } else {
            enableDisableAllCbConfigButtons(false);
        }
        this.mPhone.getCellBroadcastSmsConfig(Message.obtain(this.mHandler, 2));
    }

    @Override // android.app.Activity
    protected void onPause() {
        super.onPause();
        CellBroadcastSmsConfig.setCbSmsNoOfStructs(31);
        this.mPhone.setCellBroadcastSmsConfig(CellBroadcastSmsConfig.getCbSmsAllValues(), Message.obtain(this.mHandler, 3));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void enableDisableAllCbConfigButtons(boolean enable) {
        this.mButtonEmergencyBroadcast.setEnabled(enable);
        this.mListLanguage.setEnabled(enable);
        this.mButtonAdministrative.setEnabled(enable);
        this.mButtonMaintenance.setEnabled(enable);
        this.mButtonLocalWeather.setEnabled(enable);
        this.mButtonAtr.setEnabled(enable);
        this.mButtonLafs.setEnabled(enable);
        this.mButtonRestaurants.setEnabled(enable);
        this.mButtonLodgings.setEnabled(enable);
        this.mButtonRetailDirectory.setEnabled(enable);
        this.mButtonAdvertisements.setEnabled(enable);
        this.mButtonStockQuotes.setEnabled(enable);
        this.mButtonEo.setEnabled(enable);
        this.mButtonMhh.setEnabled(enable);
        this.mButtonTechnologyNews.setEnabled(enable);
        this.mButtonMultiCategory.setEnabled(enable);
        this.mButtonLocal1.setEnabled(enable);
        this.mButtonRegional1.setEnabled(enable);
        this.mButtonNational1.setEnabled(enable);
        this.mButtonInternational1.setEnabled(enable);
        this.mButtonLocal2.setEnabled(enable);
        this.mButtonRegional2.setEnabled(enable);
        this.mButtonNational2.setEnabled(enable);
        this.mButtonInternational2.setEnabled(enable);
        this.mButtonLocal3.setEnabled(enable);
        this.mButtonRegional3.setEnabled(enable);
        this.mButtonNational3.setEnabled(enable);
        this.mButtonInternational3.setEnabled(enable);
        this.mButtonLocal4.setEnabled(enable);
        this.mButtonRegional4.setEnabled(enable);
        this.mButtonNational4.setEnabled(enable);
        this.mButtonInternational4.setEnabled(enable);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void setAllCbConfigButtons(int[] configArray) {
        this.mButtonEmergencyBroadcast.setChecked(configArray[1] != 0);
        this.mListLanguage.setValueIndex(CellBroadcastSmsConfig.getConfigDataLanguage() - 1);
        this.mButtonAdministrative.setChecked(configArray[2] != 0);
        this.mButtonMaintenance.setChecked(configArray[3] != 0);
        this.mButtonLocalWeather.setChecked(configArray[20] != 0);
        this.mButtonAtr.setChecked(configArray[21] != 0);
        this.mButtonLafs.setChecked(configArray[22] != 0);
        this.mButtonRestaurants.setChecked(configArray[23] != 0);
        this.mButtonLodgings.setChecked(configArray[24] != 0);
        this.mButtonRetailDirectory.setChecked(configArray[25] != 0);
        this.mButtonAdvertisements.setChecked(configArray[26] != 0);
        this.mButtonStockQuotes.setChecked(configArray[27] != 0);
        this.mButtonEo.setChecked(configArray[28] != 0);
        this.mButtonMhh.setChecked(configArray[29] != 0);
        this.mButtonTechnologyNews.setChecked(configArray[30] != 0);
        this.mButtonMultiCategory.setChecked(configArray[31] != 0);
        this.mButtonLocal1.setChecked(configArray[4] != 0);
        this.mButtonRegional1.setChecked(configArray[5] != 0);
        this.mButtonNational1.setChecked(configArray[6] != 0);
        this.mButtonInternational1.setChecked(configArray[7] != 0);
        this.mButtonLocal2.setChecked(configArray[8] != 0);
        this.mButtonRegional2.setChecked(configArray[9] != 0);
        this.mButtonNational2.setChecked(configArray[10] != 0);
        this.mButtonInternational2.setChecked(configArray[11] != 0);
        this.mButtonLocal3.setChecked(configArray[12] != 0);
        this.mButtonRegional3.setChecked(configArray[13] != 0);
        this.mButtonNational3.setChecked(configArray[14] != 0);
        this.mButtonInternational3.setChecked(configArray[15] != 0);
        this.mButtonLocal4.setChecked(configArray[16] != 0);
        this.mButtonRegional4.setChecked(configArray[17] != 0);
        this.mButtonNational4.setChecked(configArray[18] != 0);
        this.mButtonInternational4.setChecked(configArray[19] != 0);
    }

    private class MyHandler extends Handler {
        private MyHandler() {
        }

        @Override // android.os.Handler
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 1:
                case 3:
                    break;
                case 2:
                    int[] result = (int[]) ((AsyncResult) msg.obj).result;
                    if (result[0] == 0) {
                        result[0] = 31;
                        CellBroadcastSms.this.mButtonBcSms.setChecked(false);
                        CellBroadcastSms.this.mPhone.activateCellBroadcastSms(1, Message.obtain(CellBroadcastSms.this.mHandler, 1));
                        Settings.Global.putInt(CellBroadcastSms.this.mPhone.getContext().getContentResolver(), "cdma_cell_broadcast_sms", 1);
                        CellBroadcastSms.this.enableDisableAllCbConfigButtons(false);
                    }
                    CellBroadcastSmsConfig.setCbSmsConfig(result);
                    CellBroadcastSms.this.setAllCbConfigButtons(CellBroadcastSmsConfig.getCbSmsBselectedValues());
                    break;
                default:
                    Log.e("CellBroadcastSms", "Error! Unhandled message in CellBroadcastSms.java. Message: " + msg.what);
                    break;
            }
        }
    }

    private static final class CellBroadcastSmsConfig {
        private static int[] mBSelected = new int[32];
        private static int[] mConfigDataComplete = new int[94];

        private CellBroadcastSmsConfig() {
        }

        /* JADX INFO: Access modifiers changed from: private */
        public static void setCbSmsConfig(int[] configData) {
            if (configData == null) {
                Log.e("CellBroadcastSms", "Error! No cell broadcast service categories returned.");
                return;
            }
            if (configData[0] > 94) {
                Log.e("CellBroadcastSms", "Error! Wrong number of service categories returned from RIL");
                return;
            }
            for (int i = 1; i < configData.length; i += 3) {
                mBSelected[configData[i]] = configData[i + 2];
            }
            mConfigDataComplete = configData;
        }

        /* JADX INFO: Access modifiers changed from: private */
        public static void setCbSmsBSelectedValue(boolean value, int pos) {
            if (pos < mBSelected.length) {
                mBSelected[pos] = !value ? 0 : 1;
            } else {
                Log.e("CellBroadcastSms", "Error! Invalid value position.");
            }
        }

        /* JADX INFO: Access modifiers changed from: private */
        public static int[] getCbSmsBselectedValues() {
            return mBSelected;
        }

        /* JADX INFO: Access modifiers changed from: private */
        public static int[] getCbSmsAllValues() {
            return mConfigDataComplete;
        }

        /* JADX INFO: Access modifiers changed from: private */
        public static void setCbSmsNoOfStructs(int value) {
            mConfigDataComplete[0] = value;
        }

        /* JADX INFO: Access modifiers changed from: private */
        public static void setConfigDataCompleteBSelected(boolean value, int serviceCategory) {
            for (int i = 1; i < mConfigDataComplete.length; i += 3) {
                if (mConfigDataComplete[i] == serviceCategory) {
                    mConfigDataComplete[i + 2] = !value ? 0 : 1;
                    return;
                }
            }
        }

        /* JADX INFO: Access modifiers changed from: private */
        public static void setConfigDataCompleteLanguage(int language) {
            for (int i = 2; i < mConfigDataComplete.length; i += 3) {
                mConfigDataComplete[i] = language;
            }
        }

        /* JADX INFO: Access modifiers changed from: private */
        public static int getConfigDataLanguage() {
            int language = mConfigDataComplete[2];
            if (language < 1 || language > 7) {
                Log.e("CellBroadcastSms", "Error! Wrong language returned from RIL...defaulting to 1, english");
                return 1;
            }
            return language;
        }
    }
}
