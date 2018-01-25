package org.mypico.android.delegate;

import android.content.Context;

import com.example.android.wizardpager.wizard.model.AbstractWizardModel;
import com.example.android.wizardpager.wizard.model.BranchPage;
import com.example.android.wizardpager.wizard.model.PageList;
import com.example.android.wizardpager.wizard.model.SingleFixedChoicePage;

/**
 * Model used to create the rules wizard
 * See android-wizardpager on the following pages:
 * https://plus.google.com/+RomanNurik/posts/6cVymZvn3f4
 * https://github.com/romannurik/android-wizardpager
 *
 * @author David Llewellyn-Jones <David.Llewellyn-Jones@cl.cam.ac.uk>
 */
public class RulesWizardModel extends AbstractWizardModel {
    /**
     * Constructor
     *
     * @param context
     */
    public RulesWizardModel(Context context) {
        super(context);
    }

    /* (non-Javadoc)
     * Establish the pages that form the Wizard
     *
     * @see com.example.android.wizardpager.wizard.model.AbstractWizardModel#onNewRootPageList()
     */
    @Override
    protected PageList onNewRootPageList() {
        return new PageList(new BranchPage(this, "Date restriction")
            .addBranch("1 day")
            .addBranch("1 week")
            .addBranch("Indefinite")
            .addBranch(
                "Other",
                new CalendarPage(this, "Delegate until")).setValue("1 day"),
            new BranchPage(this, "Usage limit")
                .addBranch("Once")
                .addBranch("Twice")
                .addBranch("Unlimited")
                .addBranch(
                    "Other",
                    new SingleFixedChoicePage(this, "Custom usage limit")
                        .setChoices("Some", "All").setValue("Some")).setValue("Once"),
            new SingleFixedChoicePage(this, "Limit access").setChoices(
                "Read content", "Edit and post", "Full rights").setValue("Read content"));
    }
}
