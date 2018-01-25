package org.mypico.android.delegate;

import java.util.List;

import org.mypico.android.data.SafeLensPairing;
import org.mypico.android.pairing.LensPairingDetailActivity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.mypico.jpico.data.pairing.Pairing;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import com.example.android.wizardpager.R;
import com.example.android.wizardpager.wizard.model.AbstractWizardModel;
import com.example.android.wizardpager.wizard.model.ModelCallbacks;
import com.example.android.wizardpager.wizard.model.Page;
import com.example.android.wizardpager.wizard.ui.PageFragmentCallbacks;
import com.example.android.wizardpager.wizard.ui.ReviewFragment;
import com.example.android.wizardpager.wizard.ui.StepPagerStrip;

/**
 * Wizard for setting up the delegation rules. For more info about the
 * android-wizardpager this uses, see the following pages:
 * https://plus.google.com/+RomanNurik/posts/6cVymZvn3f4
 * https://github.com/romannurik/android-wizardpager
 *
 * @author David Llewellyn-Jones <David.Llewellyn-Jones@cl.cam.ac.uk>
 */
public class RulesActivity extends FragmentActivity implements
    PageFragmentCallbacks, ReviewFragment.Callbacks, ModelCallbacks {

    /**
     * Use to log output messages to the LogCat console
     */
    private static final Logger LOGGER = LoggerFactory
        .getLogger(DelegateActivity.class.getSimpleName());

    /**
     * Extended data for the intent. Stores the details of the {@link Pairing}
     * for delegation as a SafeLensPairing structure (using the Parcelable
     * interface)
     */
    public static final String PAIRING = LensPairingDetailActivity.class
        .getCanonicalName() + "pairing";

    private static final int DELEGATE_QR = 0;


    /**
     * Object for storing the {@link Pairing} to be delegated
     */
    private SafeLensPairing pairing;

    private ViewPager mPager;
    private MyPagerAdapter mPagerAdapter;
    private boolean mEditingAfterReview;
    private AbstractWizardModel mWizardModel = new RulesWizardModel(this);
    private boolean mConsumePageSelectedEvent;
    private Button mNextButton;
    private Button mPrevButton;
    private List<Page> mCurrentPageSequence;
    private StepPagerStrip mStepPagerStrip;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Get safe lens pairing from received intent
        if (getIntent().hasExtra(PAIRING)) {
            pairing = (SafeLensPairing) getIntent().getParcelableExtra(PAIRING);
        } else {
            LOGGER.warn("safe pairing extra missing, finishing activity");
            finish();
        }

        if (savedInstanceState != null) {
            mWizardModel.load(savedInstanceState.getBundle("model"));
        }

        mWizardModel.registerListener(this);

        mPagerAdapter = new MyPagerAdapter(getSupportFragmentManager());
        mPager = (ViewPager) findViewById(R.id.pager);
        mPager.setAdapter(mPagerAdapter);
        mStepPagerStrip = (StepPagerStrip) findViewById(R.id.strip);
        mStepPagerStrip
            .setOnPageSelectedListener(new StepPagerStrip.OnPageSelectedListener() {
                @Override
                public void onPageStripSelected(int position) {
                    position = Math.min(mPagerAdapter.getCount() - 1,
                        position);
                    if (mPager.getCurrentItem() != position) {
                        mPager.setCurrentItem(position);
                    }
                }
            });

        mNextButton = (Button) findViewById(R.id.next_button);
        mPrevButton = (Button) findViewById(R.id.prev_button);

        mPager.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                mStepPagerStrip.setCurrentPage(position);

                if (mConsumePageSelectedEvent) {
                    mConsumePageSelectedEvent = false;
                    return;
                }

                mEditingAfterReview = false;
                updateBottomBar();
            }
        });

        mNextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mPager.getCurrentItem() == mCurrentPageSequence.size()) {
                    // This is the last page, so the user has clicked to finalise things
                    Context context = view.getContext();
                    final Intent intent = new Intent(context,
                        DelegateActivity.class);

                    // Store the current pairing info in the intent
                    intent.putExtra(DelegateActivity.PAIRING, pairing);

                    // Time to move on
                    startActivityForResult(intent, DELEGATE_QR);
                } else {
                    if (mEditingAfterReview) {
                        // Move to a different page
                        mPager.setCurrentItem(mPagerAdapter.getCount() - 1);
                    } else {
                        // Move to the next page
                        mPager.setCurrentItem(mPager.getCurrentItem() + 1);
                    }
                }
            }
        });

        mPrevButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Move to the previous page
                mPager.setCurrentItem(mPager.getCurrentItem() - 1);
            }
        });

        onPageTreeChanged();
        updateBottomBar();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        switch (requestCode) {
            case (DELEGATE_QR):
                if (resultCode == Activity.RESULT_OK) {
                    setResult(RESULT_OK);
                    finish();
                }
                break;
            default:
                // Do nothing
                break;
        }
    }


    @Override
    public void onPageTreeChanged() {
        mCurrentPageSequence = mWizardModel.getCurrentPageSequence();
        recalculateCutOffPage();
        mStepPagerStrip.setPageCount(mCurrentPageSequence.size() + 1); // + 1 =
        // review
        // step
        mPagerAdapter.notifyDataSetChanged();
        updateBottomBar();
    }

    /**
     * Update the bottom bar of the wizard to reflect whether this is the last page or not.
     */
    private void updateBottomBar() {
        int position = mPager.getCurrentItem();
        if (position == mCurrentPageSequence.size()) {
            mNextButton.setText("Generate delegation");
            mNextButton.setBackgroundResource(R.drawable.finish_background);
            mNextButton.setTextAppearance(this, R.style.TextAppearanceFinish);
        } else {
            mNextButton.setText(mEditingAfterReview ? R.string.review
                : R.string.next);
            mNextButton
                .setBackgroundResource(R.drawable.selectable_item_background);
            TypedValue v = new TypedValue();
            getTheme().resolveAttribute(android.R.attr.textAppearanceMedium, v,
                true);
            mNextButton.setTextAppearance(this, v.resourceId);
            mNextButton.setEnabled(position != mPagerAdapter.getCutOffPage());
        }

        mPrevButton
            .setVisibility(position <= 0 ? View.INVISIBLE : View.VISIBLE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mWizardModel.unregisterListener(this);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBundle("model", mWizardModel.save());
    }

    @Override
    public AbstractWizardModel onGetModel() {
        return mWizardModel;
    }

    @Override
    public void onEditScreenAfterReview(String key) {
        for (int i = mCurrentPageSequence.size() - 1; i >= 0; i--) {
            if (mCurrentPageSequence.get(i).getKey().equals(key)) {
                mConsumePageSelectedEvent = true;
                mEditingAfterReview = true;
                mPager.setCurrentItem(i);
                updateBottomBar();
                break;
            }
        }
    }

    @Override
    public void onPageDataChanged(Page page) {
        if (page.isRequired()) {
            if (recalculateCutOffPage()) {
                mPagerAdapter.notifyDataSetChanged();
                updateBottomBar();
            }
        }
    }

    @Override
    public Page onGetPage(String key) {
        return mWizardModel.findByKey(key);
    }

    /**
     * @return
     */
    private boolean recalculateCutOffPage() {
        // Cut off the pager adapter at first required page that isn't completed
        int cutOffPage = mCurrentPageSequence.size() + 1;
        for (int i = 0; i < mCurrentPageSequence.size(); i++) {
            Page page = mCurrentPageSequence.get(i);
            if (page.isRequired() && !page.isCompleted()) {
                cutOffPage = i;
                break;
            }
        }

        if (mPagerAdapter.getCutOffPage() != cutOffPage) {
            mPagerAdapter.setCutOffPage(cutOffPage);
            return true;
        }

        return false;
    }

    /**
     * @author David Llewellyn-Jones <David.Llewellyn-Jones@cl.cam.ac.uk>
     */
    public class MyPagerAdapter extends FragmentStatePagerAdapter {
        private int mCutOffPage;
        private Fragment mPrimaryItem;

        public MyPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int i) {
            if (i >= mCurrentPageSequence.size()) {
                return new ReviewFragment();
            }

            return mCurrentPageSequence.get(i).createFragment();
        }

        @Override
        public int getItemPosition(Object object) {
            // TODO: be smarter about this
            if (object == mPrimaryItem) {
                // Re-use the current fragment (its position never changes)
                return POSITION_UNCHANGED;
            }

            return POSITION_NONE;
        }

        @Override
        public void setPrimaryItem(ViewGroup container, int position,
                                   Object object) {
            super.setPrimaryItem(container, position, object);
            mPrimaryItem = (Fragment) object;
        }

        @Override
        public int getCount() {
            if (mCurrentPageSequence == null) {
                return 0;
            }
            return Math.min(mCutOffPage + 1, mCurrentPageSequence.size() + 1);
        }

        /**
         * Set the total number of pages to be displayed by the delegation wizard.
         *
         * @param cutOffPage The total number of pages to set.
         */
        public void setCutOffPage(int cutOffPage) {
            if (cutOffPage < 0) {
                cutOffPage = Integer.MAX_VALUE;
            }
            mCutOffPage = cutOffPage;
        }

        /**
         * Get the total number of pages to be displayed by the delegation wizard.
         *
         * @return The total number of pages.
         */
        public int getCutOffPage() {
            return mCutOffPage;
        }
    }
}
