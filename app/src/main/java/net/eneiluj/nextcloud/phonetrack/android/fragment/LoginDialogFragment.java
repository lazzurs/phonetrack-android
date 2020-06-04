package net.eneiluj.nextcloud.phonetrack.android.fragment;

import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import android.util.Log;

import com.nextcloud.android.sso.AccountImporter;
import com.nextcloud.android.sso.exceptions.AccountImportCancelledException;
import com.nextcloud.android.sso.exceptions.AndroidGetAccountsPermissionNotGranted;
import com.nextcloud.android.sso.exceptions.NextcloudFilesAppNotInstalledException;
import com.nextcloud.android.sso.model.SingleSignOnAccount;
import com.nextcloud.android.sso.ui.UiExceptionManager;

import net.eneiluj.nextcloud.phonetrack.android.activity.SettingsActivity;

public class LoginDialogFragment extends DialogFragment {
    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        try {
            AccountImporter.pickNewAccount(this);
        } catch (NextcloudFilesAppNotInstalledException e) {
            UiExceptionManager.showDialogForException(getContext(), e);
            Log.w("PhoneTrack", "=============================================================");
            Log.w("PhoneTrack", "Nextcloud app is not installed. Cannot choose account");
            e.printStackTrace();
        } catch (AndroidGetAccountsPermissionNotGranted e) {
            UiExceptionManager.showDialogForException(getContext(), e);
            Log.w("PhoneTrack", "=============================================================");
            Log.w("PhoneTrack", "Impossible to get 'account permission'. Cannot choose account");
            e.printStackTrace();
        }
        return super.onCreateDialog(savedInstanceState);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        try {
            AccountImporter.onActivityResult(requestCode, resultCode, data, LoginDialogFragment.this, (SingleSignOnAccount account) -> {
                ((SettingsActivity) getActivity()).onAccountChoose(account);
            });
        } catch (AccountImportCancelledException e) {
            Log.v("PhoneTrack", "Account import was cancelled");
        }
    }
}
