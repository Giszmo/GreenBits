package com.greenaddress.greenbits.ui;

import android.app.Dialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Html;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.blockstream.libwally.Wally;
import com.google.common.base.Function;
import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.greenaddress.greenapi.GAException;
import com.greenaddress.greenapi.Network;
import com.greenaddress.greenapi.Output;
import com.greenaddress.greenapi.PreparedTransaction;
import com.greenaddress.greenbits.GaService;
import com.greenaddress.greenbits.wallets.TrezorHWWallet;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.Utils;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.script.ScriptChunk;
import org.bitcoinj.utils.MonetaryFormat;

import java.net.URI;
import java.net.URISyntaxException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class TransactionActivity extends GaActivity {

    @Override
    protected int getMainViewId() { return R.layout.activity_transaction; }

    @Override
    protected void onCreateWithService(final Bundle savedInstanceState) {
        if (savedInstanceState == null)
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.container, new PlaceholderFragment())
                    .commit();
        setResult(RESULT_OK);
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_transaction, menu);
        return true;
    }


    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {

        final int id = item.getItemId();

        if (id == R.id.action_settings) {
            return true;
        } else if (id == R.id.action_share) {
            final TransactionItem txItem = (TransactionItem) getIntent().getSerializableExtra("TRANSACTION");
            final Intent sendIntent = new Intent();
            sendIntent.setAction(Intent.ACTION_SEND);
            sendIntent.putExtra(Intent.EXTRA_TEXT, Network.BLOCKEXPLORER_TX + txItem.txhash);
            sendIntent.setType("text/plain");
            startActivity(sendIntent);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * A placeholder fragment containing a simple view.
     */
    public static class PlaceholderFragment extends GAFragment {

        private static final String TAG = PlaceholderFragment.class.getSimpleName();
        private Dialog mSummary;
        private Dialog mTwoFactor;

        private void openInBrowser(final TextView textView, final String identifier, final String url) {
            textView.setText(identifier);
            textView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {

                    String domain = url;
                    try {
                        domain = new URI(url).getHost();
                    } catch (final URISyntaxException e) {
                        e.printStackTrace();
                    }

                    final String stripped = domain.startsWith("www.") ? domain.substring(4) : domain;

                    UI.popup(getActivity(), R.string.warning, R.string.continueText, R.string.cancel)
                        .content(getString(R.string.view_block_explorer, stripped))
                        .onPositive(new MaterialDialog.SingleButtonCallback() {
                            @Override
                            public void onClick(final MaterialDialog dlg, final DialogAction which) {
                                final String fullUrl = TextUtils.concat(url, identifier).toString();
                                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(fullUrl)));
                            }
                        }).build().show();
                }
            });
        }

        @Override
        public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
                                 final Bundle savedInstanceState) {
            final View rootView = inflater.inflate(R.layout.fragment_transaction, container, false);

            final TextView hashText = (TextView) rootView.findViewById(R.id.txHashText);

            final TextView amount = (TextView) rootView.findViewById(R.id.txAmountText);
            final TextView bitcoinScale = (TextView) rootView.findViewById(R.id.txBitcoinScale);
            final TextView bitcoinUnit = (TextView) rootView.findViewById(R.id.txBitcoinUnit);

            final TextView dateText = (TextView) rootView.findViewById(R.id.txDateText);
            final TextView memoText = (TextView) rootView.findViewById(R.id.txMemoText);

            final TextView memoEdit = (TextView) rootView.findViewById(R.id.sendToNoteIcon);
            final EditText memoEditText = (EditText) rootView.findViewById(R.id.sendToNoteText);

            final TextView doubleSpentByText = (TextView) rootView.findViewById(R.id.txDoubleSpentByText);
            final TextView doubleSpentByTitle = (TextView) rootView.findViewById(R.id.txDoubleSpentByTitle);

            final TextView recipientText = (TextView) rootView.findViewById(R.id.txRecipientText);
            final TextView recipientTitle = (TextView) rootView.findViewById(R.id.txRecipientTitle);

            final TextView receivedOnText = (TextView) rootView.findViewById(R.id.txReceivedOnText);
            final TextView receivedOnTitle = (TextView) rootView.findViewById(R.id.txReceivedOnTitle);

            final TextView unconfirmedText = (TextView) rootView.findViewById(R.id.txUnconfirmedText);
            final TextView unconfirmedEstimatedBlocks = (TextView) rootView.findViewById(R.id.txUnconfirmedEstimatedBlocks);
            final TextView unconfirmedRecommendation = (TextView) rootView.findViewById(R.id.txUnconfirmedRecommendation);
            final Button unconfirmedIncreaseFee = (Button) rootView.findViewById(R.id.txUnconfirmedIncreaseFee);
            final Button saveMemo = (Button) rootView.findViewById(R.id.saveMemo);

            final TextView feeScale = (TextView) rootView.findViewById(R.id.txFeeScale);
            final TextView feeUnit = (TextView) rootView.findViewById(R.id.txFeeUnit);
            final TextView feeInfoText = (TextView) rootView.findViewById(R.id.txFeeInfoText);

            final TransactionItem txItem = (TransactionItem) getActivity().getIntent().getSerializableExtra("TRANSACTION");
            final GaActivity gaActivity = getGaActivity();

            openInBrowser(hashText, txItem.txhash, Network.BLOCKEXPLORER_TX);

            final Coin fee = Coin.valueOf(txItem.fee);
            final Coin feePerKb;
            if (txItem.size > 0) {
                feePerKb = Coin.valueOf(1000 * txItem.fee / txItem.size);
            } else {
                // shouldn't happen, but just in case let's avoid division by zero
                feePerKb = Coin.valueOf(0);
            }

            final boolean isWatchOnly = getGAService().isWatchOnly();

            if (txItem.type.equals(TransactionItem.TYPE.OUT) || txItem.type.equals(TransactionItem.TYPE.REDEPOSIT) || txItem.isSpent) {
                if (txItem.getConfirmations() > 0) {
                    // confirmed - hide unconfirmed widgets
                    rootView.findViewById(R.id.txUnconfirmed).setVisibility(View.GONE);
                    unconfirmedRecommendation.setVisibility(View.GONE);
                    unconfirmedIncreaseFee.setVisibility(View.GONE);
                    unconfirmedEstimatedBlocks.setVisibility(View.GONE);
                } else if (txItem.type.equals(TransactionItem.TYPE.OUT) || txItem.type.equals(TransactionItem.TYPE.REDEPOSIT)) {
                    // unconfirmed outgoing output/redeposit - can be RBF'd
                    int currentEstimate = 25, bestEstimate;
                    final Map<String, Object> feeEstimates = getGAService().getLoginData().feeEstimates;
                    final String checkValues[] = {"1", "3", "6"};
                    for (final String value : checkValues) {
                        final double feerate = Double.parseDouble(((Map)feeEstimates.get(value)).get("feerate").toString());
                        if (feePerKb.compareTo(Coin.valueOf((long)(feerate*1000*1000*100))) >= 0) {
                            currentEstimate = (Integer)((Map)feeEstimates.get(value)).get("blocks");
                            break;
                        }
                    }
                    bestEstimate = (Integer)((Map)feeEstimates.get("1")).get("blocks");
                    unconfirmedEstimatedBlocks.setText(String.format(getResources().getString(R.string.willConfirmAfter), currentEstimate));
                    if (bestEstimate < currentEstimate && txItem.replaceable && !isWatchOnly) {
                        if (bestEstimate == 1) {
                            unconfirmedRecommendation.setText(R.string.recommendationSingleBlock);
                        } else {
                            unconfirmedRecommendation.setText(String.format(getResources().getString(R.string.recommendationBlocks), bestEstimate));
                        }
                        unconfirmedIncreaseFee.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(final View v) {
                                final double feerate = Double.parseDouble(((Map) feeEstimates.get("1")).get("feerate").toString());
                                final Coin feerateCoin = Coin.valueOf((long) (feerate * 1000 * 1000 * 100));
                                replaceByFee(txItem, feerateCoin, null, 0);
                            }
                        });
                    } else {
                        unconfirmedIncreaseFee.setVisibility(View.GONE);
                        unconfirmedRecommendation.setVisibility(View.GONE);
                    }
                } else {
                    // incoming spent - hide outgoing-only widgets
                    unconfirmedRecommendation.setVisibility(View.GONE);
                    unconfirmedIncreaseFee.setVisibility(View.GONE);
                    unconfirmedEstimatedBlocks.setVisibility(View.GONE);
                }
            } else {
                // unspent incoming output
                // incoming - hide outgoing-only widgets
                unconfirmedRecommendation.setVisibility(View.GONE);
                unconfirmedIncreaseFee.setVisibility(View.GONE);
                unconfirmedEstimatedBlocks.setVisibility(View.GONE);
                if (txItem.getConfirmations() > 0) {
                    if (isWatchOnly || txItem.spvVerified ) {
                        rootView.findViewById(R.id.txUnconfirmed).setVisibility(View.GONE);
                    } else {
                        if (getGAService().spv.getSpvBlocksLeft() != Integer.MAX_VALUE) {
                            unconfirmedText.setText(String.format("%s %s", getResources().getString(R.string.txUnverifiedTx),
                                    getGAService().spv.getSpvBlocksLeft()));
                        } else {
                            unconfirmedText.setText(String.format("%s %s", getResources().getString(R.string.txUnverifiedTx),
                                    "Not yet connected to SPV!"));
                        }
                    }
                }
            }

            final String btcUnit = (String) getGAService().getUserConfig("unit");
            final Coin coin = Coin.valueOf(txItem.amount);
            final MonetaryFormat bitcoinFormat = CurrencyMapper.mapBtcUnitToFormat(btcUnit);
            bitcoinScale.setText(Html.fromHtml(CurrencyMapper.mapBtcUnitToPrefix(btcUnit)));
            feeScale.setText(Html.fromHtml(CurrencyMapper.mapBtcUnitToPrefix(btcUnit)));
            if (btcUnit == null || btcUnit.equals("bits")) {
                bitcoinUnit.setText("bits ");
                feeUnit.setText("bits ");
            } else {
                bitcoinUnit.setText(Html.fromHtml("&#xf15a; "));
                feeUnit.setText(Html.fromHtml("&#xf15a; "));
            }
            final String btcBalance = bitcoinFormat.noCode().format(coin).toString();
            final DecimalFormat formatter = new DecimalFormat("#,###.########");

            try {
                amount.setText(formatter.format(Double.valueOf(btcBalance)));
            } catch (final NumberFormatException e) {
                amount.setText(btcBalance);
            }


            final String btcFee = bitcoinFormat.noCode().format(fee).toString();
            final String btcFeePerKb = bitcoinFormat.noCode().format(feePerKb).toString();
            String feeInfoTextStr = "";
            try {
                feeInfoTextStr += formatter.format(Double.valueOf(btcFee));
            } catch (final NumberFormatException e) {
                feeInfoTextStr += btcFee;
            }
            feeInfoTextStr += " / " + String.valueOf(txItem.size) + " / ";
            try {
                feeInfoTextStr += formatter.format(Double.valueOf(btcFeePerKb));
            } catch (final NumberFormatException e) {
                feeInfoTextStr += btcFeePerKb;
            }
            feeInfoText.setText(feeInfoTextStr);

            dateText.setText(SimpleDateFormat.getInstance().format(txItem.date));
            if (txItem.memo != null && txItem.memo.length() > 0) {
                memoText.setText(txItem.memo);
                if (isWatchOnly)
                    memoEdit.setVisibility(View.GONE);
            } else {
                memoText.setVisibility(View.GONE);
                rootView.findViewById(R.id.txMemoMargin).setVisibility(View.GONE);
                if (isWatchOnly) {
                    rootView.findViewById(R.id.txMemoTitle).setVisibility(View.GONE);
                    memoEdit.setVisibility(View.GONE);
                }
            }
            // FIXME: use a list instead of reusing a TextView to show all double spends to allow
            // for a warning to be shown before the browser is open
            // this is to prevent to accidentally leak to block explorers your addresses
            if (txItem.doubleSpentBy != null || txItem.replacedHashes.size() > 0) {
                CharSequence res = "";
                if (txItem.doubleSpentBy != null) {
                    if (txItem.doubleSpentBy.equals("malleability") || txItem.doubleSpentBy.equals("update")) {
                        res = txItem.doubleSpentBy;
                    } else {
                        res = Html.fromHtml("<a href=\"" + Network.BLOCKEXPLORER_TX + "" + txItem.doubleSpentBy + "\">" + txItem.doubleSpentBy + "</a>");
                    }
                    if (txItem.replacedHashes.size() > 0) {
                        res = TextUtils.concat(res, "; ");
                    }
                }
                if (txItem.replacedHashes.size() > 0) {
                    res = TextUtils.concat(res, Html.fromHtml("replaces transactions:<br/>"));
                    for (int i = 0; i < txItem.replacedHashes.size(); ++i) {
                        if (i > 0) {
                            res = TextUtils.concat(res, Html.fromHtml("<br/>"));
                        }
                        String txhash = txItem.replacedHashes.get(i);
                        res = TextUtils.concat(res, Html.fromHtml("<a href=\"" + Network.BLOCKEXPLORER_TX + "" + txhash + "\">" + txhash + "</a>"));
                    }
                }
                doubleSpentByText.setText(res);
            } else {
                doubleSpentByText.setVisibility(View.GONE);
                doubleSpentByTitle.setVisibility(View.GONE);
                rootView.findViewById(R.id.txDoubleSpentByMargin).setVisibility(View.GONE);
            }

            if (txItem.counterparty != null && txItem.counterparty.length() > 0) {
                recipientText.setText(txItem.counterparty);
            } else {
                recipientText.setVisibility(View.GONE);
                recipientTitle.setVisibility(View.GONE);
                rootView.findViewById(R.id.txRecipientMargin).setVisibility(View.GONE);
            }

            if (txItem.receivedOn != null && txItem.receivedOn.length() > 0) {
                openInBrowser(receivedOnText, txItem.receivedOn, Network.BLOCKEXPLORER_ADDRESS);
            } else {
                receivedOnText.setVisibility(View.GONE);
                receivedOnTitle.setVisibility(View.GONE);
                rootView.findViewById(R.id.txReceivedOnMargin).setVisibility(View.GONE);
            }

            if (isWatchOnly)
                return rootView;

            memoEdit.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(final View view) {
                    final boolean editVisible = memoEditText.getVisibility() == View.VISIBLE;
                    memoEditText.setText(memoText.getText().toString());
                    memoEditText.setVisibility(editVisible ? View.GONE : View.VISIBLE);
                    saveMemo.setVisibility(editVisible ? View.GONE : View.VISIBLE);
                    memoText.setVisibility(editVisible ? View.VISIBLE : View.GONE);
                }
            });

            saveMemo.setOnClickListener(new View.OnClickListener() {

                private void onDisableEdit() {
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            memoText.setText(memoEditText.getText().toString());
                            saveMemo.setVisibility(View.GONE);
                            memoEditText.setVisibility(View.GONE);
                            if (memoText.getText().toString().isEmpty()) {
                                memoText.setVisibility(View.GONE);
                                rootView.findViewById(R.id.txMemoMargin).setVisibility(View.GONE);
                            } else {
                                rootView.findViewById(R.id.txMemoMargin).setVisibility(View.VISIBLE);
                                memoText.setVisibility(View.VISIBLE);
                            }
                        }
                    });
                }

                @Override
                public void onClick(final View view) {
                    final String edited = memoEditText.getText().toString();
                    if (!edited.equals(memoText.getText().toString())) {
                        CB.after(getGAService().changeMemo(txItem.txhash, edited),
                                new CB.Toast<Boolean>(gaActivity) {
                                    @Override
                                    public void onSuccess(final Boolean result) {
                                        onDisableEdit();
                                    }
                                });
                    } else {
                        onDisableEdit();
                    }
                }
            });

            return rootView;
        }

        private void replaceByFee(final TransactionItem txItem, final Coin feerate, Integer txSize, final int level) {
            if (level > 10) {
                throw new RuntimeException("Recursion limit exceeded");
            }
            final GaActivity gaActivity = getGaActivity();

            final Transaction tx = new Transaction(Network.NETWORK, Wally.hex_to_bytes(txItem.data));
            Integer change_pointer = null;
            final int subAccount = getGAService().getCurrentSubAccount();
            // requiredFeeDelta assumes mintxfee = 1000, and inputs increasing
            // by at most 4 bytes per input (signatures have variable lengths)
            if (txSize == null) {
                txSize = tx.getMessageSize();
            }
            long requiredFeeDelta = txSize + tx.getInputs().size() * 4;
            List<TransactionInput> oldInputs = new ArrayList<>(tx.getInputs());
            tx.clearInputs();
            for (int i = 0; i < txItem.eps.size(); ++i) {
                final Map<String, Object> ep = (Map<String, Object>) txItem.eps.get(i);
                if (((Boolean) ep.get("is_credit"))) continue;
                TransactionInput oldInput = oldInputs.get((Integer) ep.get("pt_idx"));
                TransactionInput newInput = new TransactionInput(
                        Network.NETWORK,
                        null,
                        oldInput.getScriptBytes(),
                        oldInput.getOutpoint(),
                        Coin.valueOf(Long.valueOf((String) ep.get("value")))
                );
                newInput.setSequenceNumber(0);
                tx.addInput(newInput);
            }
            final Coin newFeeWithRate = feerate.multiply(txSize).divide(1000);
            Coin feeDelta = Coin.valueOf(Math.max(
                    newFeeWithRate.subtract(tx.getFee()).longValue(),
                    requiredFeeDelta
            ));
            final Coin oldFee = tx.getFee();
            Coin remainingFeeDelta = feeDelta;
            List<TransactionOutput> origOuts = new ArrayList<>(tx.getOutputs());
            tx.clearOutputs();
            for (int i = 0; i < txItem.eps.size(); ++i) {
                final Map<String, Object> ep = (Map<String, Object>) txItem.eps.get(i);
                if (!((Boolean) ep.get("is_credit"))) continue;

                if (!((Boolean) ep.get("is_relevant"))) {
                    // keep non-change/non-redeposit intact
                    tx.addOutput(origOuts.get((Integer)ep.get("pt_idx")));
                } else {
                    if ((ep.get("subaccount") == null && subAccount == 0) ||
                            ep.get("subaccount").equals(subAccount)) {
                        change_pointer = (Integer) ep.get("pubkey_pointer");
                    }
                    // change/redeposit
                    long value = Long.valueOf((String) ep.get("value"));
                    if (Coin.valueOf(value).compareTo(remainingFeeDelta) <= 0) {
                        // smaller than remaining fee -- get rid of this output
                        remainingFeeDelta = remainingFeeDelta.subtract(
                                Coin.valueOf(value)
                        );
                    }  else {
                        // larger than remaining fee -- subtract the remaining fee
                        TransactionOutput out = origOuts.get((Integer)ep.get("pt_idx"));
                        out.setValue(out.getValue().subtract(remainingFeeDelta));
                        tx.addOutput(out);
                        remainingFeeDelta = Coin.ZERO;
                    }
                }
            }

            if (remainingFeeDelta.compareTo(Coin.ZERO) <= 0) {
                doReplaceByFee(txItem, feerate, tx, change_pointer, subAccount, oldFee, null, null, level);
                return;
            }

            final Coin finalRemaining = remainingFeeDelta;
            CB.after(getGAService().getAllUnspentOutputs(1, subAccount),
                     new CB.Toast<ArrayList>(gaActivity) {
                @Override
                public void onSuccess(ArrayList result) {
                    Coin remaining = finalRemaining;
                    final List<ListenableFuture<byte[]>> scripts = new ArrayList<>();
                    final List<Map<String, Object>> moreInputs = new ArrayList<>();
                    for (Object utxo_ : result) {
                        Map<String, Object> utxo = (Map<String, Object>) utxo_;
                        remaining = remaining.subtract(Coin.valueOf(Long.valueOf((String)utxo.get("value"))));
                        scripts.add(getGAService().createOutScript((Integer)utxo.get("subaccount"), (Integer)utxo.get("pointer")));
                        moreInputs.add(utxo);
                        if (remaining.compareTo(Coin.ZERO) <= 0)
                            break;
                    }

                    final int remainingCmp = remaining.compareTo(Coin.ZERO);
                    if (remainingCmp == 0) {
                        // Funds available exactly match the required value
                        CB.after(Futures.allAsList(scripts), new CB.Toast<List<byte[]>>(gaActivity) {
                            @Override
                            public void onSuccess(List<byte[]> morePrevouts) {
                                doReplaceByFee(txItem, feerate, tx, null, subAccount,
                                               oldFee, moreInputs, morePrevouts, level);
                            }
                        });
                        return;
                    }

                    if (remainingCmp > 0) {
                        // Not enough funds
                        gaActivity.toast(R.string.insufficientFundsText);
                        return;
                    }

                    // Funds left over - add a new change output
                    final Coin changeValue = remaining.multiply(-1);
                    CB.after(getGAService().getNewAddress(subAccount),
                             new CB.Toast<Map>(gaActivity) {
                        @Override
                        public void onSuccess(final Map result) {
                            byte[] script = Wally.hex_to_bytes((String) result.get("script"));
                            tx.addOutput(changeValue,
                                         Address.fromP2SHHash(Network.NETWORK, Utils.sha256hash160(script)));
                            CB.after(Futures.allAsList(scripts), new CB.Toast<List<byte[]>>(gaActivity) {
                                @Override
                                public void onSuccess(List<byte[]> morePrevouts) {
                                    doReplaceByFee(txItem, feerate, tx, (Integer) result.get("pointer"),
                                                   subAccount, oldFee, moreInputs, morePrevouts, level);
                                }
                            });
                        }
                    });
                }
            });
        }

        private void doReplaceByFee(final TransactionItem txItem, final Coin feerate,
                                    final Transaction tx,
                                    final Integer change_pointer, final int subAccount,
                                    final Coin oldFee, final List<Map<String, Object>> moreInputs,
                                    final List<byte[]> morePrevouts, final int level) {
            final GaActivity gaActivity = getGaActivity();

            final PreparedTransaction ptx;
            ptx = new PreparedTransaction(change_pointer, subAccount, tx,
                                          getGAService().findSubaccount("2of3", subAccount));

            for (final Map<String, Object> ep : (List<Map<String, Object>>)txItem.eps) {
                if (((Boolean) ep.get("is_credit"))) continue;
                ptx.prev_outputs.add(new Output(
                        (Integer) ep.get("subaccount"),
                        (Integer) ep.get("pubkey_pointer"),
                        1,
                        (Integer) ep.get("script_type"),
                        Wally.hex_from_bytes(tx.getInput((Integer) ep.get("pt_idx")).getScriptSig().getChunks().get(3).data),
                        tx.getInput((Integer) ep.get("pt_idx")).getValue().longValue()
                ));
            }

            int i = 0;
            if (moreInputs != null) {
                for (final Map<String, Object> ep : moreInputs) {
                    ptx.prev_outputs.add(new Output(
                            (Integer) ep.get("subaccount"),
                            (Integer) ep.get("pointer"),
                            1,
                            TransactionItem.P2SH_FORTIFIED_OUT,
                            Wally.hex_from_bytes(morePrevouts.get(i)),
                            Long.valueOf((String) ep.get("value"))
                    ));
                    tx.addInput(
                            new TransactionInput(
                                    Network.NETWORK,
                                    null,
                                    new ScriptBuilder().addChunk(
                                            // OP_0
                                            new ScriptChunk(0, new byte[0])
                                    ).data(
                                            // GA sig:
                                            new byte[71]
                                    ).data(
                                            // our sig:
                                            new byte[71]
                                    ).data(
                                            // the original outscript
                                            morePrevouts.get(i)
                                    ).build().getProgram(),
                                    new TransactionOutPoint(
                                            Network.NETWORK,
                                            (Integer) ep.get("pt_idx"),
                                            Sha256Hash.wrap(Wally.hex_to_bytes((String) ep.get("txhash")))
                                    ),
                                    Coin.valueOf(Long.valueOf((String) ep.get("value")))
                            )
                    );
                    i++;
                }
            }

            // verify if the increased fee is enough to achieve wanted feerate
            // (can be too small in case of added inputs)
            final int estimatedSize = tx.getMessageSize() + tx.getInputs().size() * 4;
            if (feerate.multiply(estimatedSize).divide(1000).compareTo(tx.getFee()) > 0) {
                replaceByFee(txItem, feerate, estimatedSize, level + 1);
                return;
            }

            // also verify if it's enough for 'bandwidth fee increment' condition
            // of RBF
            if (tx.getFee().subtract(oldFee).compareTo(Coin.valueOf(tx.getMessageSize() + tx.getInputs().size() * 4)) < 0) {
                replaceByFee(txItem, feerate, estimatedSize, level + 1);
                return;
            }

            ListenableFuture<Void> prevouts = Futures.immediateFuture(null);
            // FIXME: Find another way to do this
            if (getGAService().getSigningWallet() instanceof TrezorHWWallet) {
                for (final TransactionInput inp : tx.getInputs()) {
                    prevouts = Futures.transform(prevouts, new AsyncFunction<Void, Void>() {
                        @Override
                        public ListenableFuture<Void> apply(Void input) throws Exception {
                            return Futures.transform(
                                    getGAService().getRawOutput(inp.getOutpoint().getHash()),
                                    new Function<Transaction, Void>() {
                                        @Override
                                        public Void apply(Transaction input) {
                                            ptx.prevoutRawTxs.put(Wally.hex_from_bytes(inp.getOutpoint().getHash().getBytes()), input);
                                            return null;
                                        }
                                    }
                            );
                        }
                    });
                }
            }

            final ListenableFuture<List<byte[]>> signed = Futures.transform(prevouts, new AsyncFunction<Void, List<byte[]>>() {
                @Override
                public ListenableFuture<List<byte[]>> apply(Void input) throws Exception {
                    return getGAService().signTransaction(ptx);
                }
            });

            CB.after(signed, new CB.Toast<List<byte[]>>(gaActivity) {
                @Override
                public void onSuccess(final List<byte[]> signatures) {
                    final GaService service = getGAService();

                    int i = 0;
                    for (final byte[] sig : signatures) {
                        final TransactionInput input = tx.getInput(i++);
                        input.setScriptSig(
                                new ScriptBuilder().addChunk(
                                        // OP_0
                                        input.getScriptSig().getChunks().get(0)
                                ).data(
                                        // GA sig:
                                        new byte[] {0}
                                ).data(
                                        // our sig:
                                        sig
                                ).addChunk(
                                        // the original outscript
                                        input.getScriptSig().getChunks().get(3)
                                ).build()
                        );
                    }
                    final Map<String, Object> twoFacData = new HashMap<>();
                    twoFacData.put("try_under_limits_bump", tx.getFee().subtract(oldFee).longValue());
                    final ListenableFuture<Map<String,Object>> sendFuture = service.sendRawTransaction(tx, twoFacData, true);
                    Futures.addCallback(sendFuture, new FutureCallback<Map<String,Object>>() {
                        @Override
                        public void onSuccess(final Map result) {
                            gaActivity.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    // FIXME: Add notification with "Transaction sent"?
                                    gaActivity.finish();
                                }
                            });
                        }

                        @Override
                        public void onFailure(final Throwable t) {
                            if (!(t instanceof GAException) || !t.getMessage().equals("http://greenaddressit.com/error#auth")) {
                                gaActivity.toast(t);
                                return;
                            }
                            // 2FA is required, prompt the user
                            gaActivity.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    final boolean skipChoice = false;
                                    mTwoFactor = UI.popupTwoFactorChoice(gaActivity, service, skipChoice,
                                                                                 new CB.Runnable1T<String>() {
                                        @Override
                                        public void run(final String method) {
                                            showIncreaseSummary(method, oldFee, tx.getFee(), tx);
                                        }
                                    });
                                    if (mTwoFactor != null)
                                        mTwoFactor.show();
                                }
                            });
                        }
                    }, service.es);
                }
            });
        }

        private void showIncreaseSummary(final String method, final Coin oldFee, final Coin newFee, final Transaction signedTx) {
            Log.i(TAG, "showIncreaseSummary( params " + method + " " + oldFee + " " + newFee + ")");
            final GaActivity gaActivity = getGaActivity();

            final String btcUnit = (String) getGAService().getUserConfig("unit");
            final MonetaryFormat bitcoinFormat = CurrencyMapper.mapBtcUnitToFormat(btcUnit);

            final View inflatedLayout = getActivity().getLayoutInflater().inflate(R.layout.dialog_new_transaction, null, false);

            ((TextView) inflatedLayout.findViewById(R.id.newTxAmountLabel)).setText(R.string.newFeeText);
            final TextView amountText = (TextView) inflatedLayout.findViewById(R.id.newTxAmountText);
            final TextView amountScale = (TextView) inflatedLayout.findViewById(R.id.newTxAmountScaleText);
            final TextView amountUnit = (TextView) inflatedLayout.findViewById(R.id.newTxAmountUnitText);
            ((TextView) inflatedLayout.findViewById(R.id.newTxFeeLabel)).setText(R.string.oldFeeText);
            final TextView feeText = (TextView) inflatedLayout.findViewById(R.id.newTxFeeText);
            final TextView feeScale = (TextView) inflatedLayout.findViewById(R.id.newTxFeeScale);
            final TextView feeUnit = (TextView) inflatedLayout.findViewById(R.id.newTxFeeUnit);

            inflatedLayout.findViewById(R.id.newTxRecipientLabel).setVisibility(View.GONE);
            inflatedLayout.findViewById(R.id.newTxRecipientText).setVisibility(View.GONE);
            final TextView twoFAText = (TextView) inflatedLayout.findViewById(R.id.newTx2FATypeText);
            final EditText newTx2FACodeText = (EditText) inflatedLayout.findViewById(R.id.newTx2FACodeText);
            final String prefix = CurrencyMapper.mapBtcFormatToPrefix(bitcoinFormat);

            amountScale.setText(Html.fromHtml(prefix));
            feeScale.setText(Html.fromHtml(prefix));
            if (prefix.isEmpty()) {
                amountUnit.setText("bits ");
                feeUnit.setText("bits ");
            } else {
                amountUnit.setText(Html.fromHtml("&#xf15a; "));
                feeUnit.setText(Html.fromHtml("&#xf15a; "));
            }
            amountText.setText(bitcoinFormat.noCode().format(newFee));
            feeText.setText(bitcoinFormat.noCode().format(oldFee));


            final Map<String, Object> twoFacData;

            if (method == null) {
                twoFAText.setVisibility(View.GONE);
                newTx2FACodeText.setVisibility(View.GONE);
                twoFacData = null;
            } else {
                twoFAText.setText(String.format("2FA %s code", method));
                twoFacData = new HashMap<>();
                twoFacData.put("method", method);
                twoFacData.put("bump_fee_amount", newFee.subtract(oldFee).longValue());
                if (!method.equals("gauth")) {
                    Map<String, Long> amount = new HashMap<>();
                    amount.put("amount", newFee.subtract(oldFee).longValue());
                    getGAService().requestTwoFacCode(method, "bump_fee", amount);
                }
            }

            mSummary = UI.popup(getActivity(), R.string.feeIncreaseTitle, R.string.send, R.string.cancel)
                    .customView(inflatedLayout, true)
                    .onPositive(new MaterialDialog.SingleButtonCallback() {
                        @Override
                        public void onClick(final MaterialDialog dialog, final DialogAction which) {
                            if (twoFacData != null) {
                                twoFacData.put("code", newTx2FACodeText.getText().toString());
                            }
                            final ListenableFuture<Map<String,Object>> sendFuture = getGAService().sendRawTransaction(signedTx, twoFacData, false);
                            Futures.addCallback(sendFuture, new CB.Toast<Map<String,Object>>(gaActivity) {
                                @Override
                                public void onSuccess(final Map result) {
                                    getActivity().runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            // FIXME: Add notification with "Transaction sent"?
                                            getActivity().finish();
                                        }
                                    });
                                }
                            }, getGAService().es);
                        }
                    })
                    .onNegative(new MaterialDialog.SingleButtonCallback() {
                        @Override
                        public void onClick(final MaterialDialog dialog, final DialogAction which) {
                            Log.i(TAG, "SHOWN ON CLOSE!");
                        }
                    })
                    .build();

            mSummary.show();
        }

        @Override
        public void onDestroyView() {
            super.onDestroyView();
            if (mSummary != null)
                mSummary.dismiss();
            if (mTwoFactor != null)
                mTwoFactor.dismiss();
        }
    }
}
