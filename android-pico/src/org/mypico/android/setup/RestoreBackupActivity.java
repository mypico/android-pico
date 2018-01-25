/*
 * (C) Copyright Cambridge Authentication Ltd, 2017
 *
 * This file is part of android-pico.
 *
 * android-pico is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * android-pico is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with android-pico. If not, see
 * <http://www.gnu.org/licenses/>.
 */


package org.mypico.android.setup;

import com.example.android.wizardpager.wizard.model.AbstractWizardModel;

/**
 * SetupActivity provides Wizards for setting up Pico and restoring a backup.
 *
 * @author Graeme Jenkinson <gcj21@cl.cam.ac.uk>
 */
public class RestoreBackupActivity extends SetupActivity {
    @Override
    AbstractWizardModel createWizardModel() {
        return new RestoreBackupWizardModel(this);
    }
}