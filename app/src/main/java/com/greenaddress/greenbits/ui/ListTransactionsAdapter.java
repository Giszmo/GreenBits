package com.greenaddress.greenbits.ui;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Color;
import android.support.v7.widget.RecyclerView;
import android.text.Html;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.greenaddress.greenbits.GaService;

import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.MonetaryFormat;

import java.text.DecimalFormat;
import java.util.List;

public class ListTransactionsAdapter extends
        RecyclerView.Adapter<ListTransactionsAdapter.ViewHolder> {

    private final static int REQUEST_TX_DETAILS = 4;

    private final List<TransactionItem> mTxItems;
    private final String mBtcUnit;
    private final Activity mActivity;
    private final GaService mService;

    public ListTransactionsAdapter(final Activity activity, final GaService service,
                                   final List<TransactionItem> txItems) {
        mTxItems = txItems;
        mBtcUnit = (String) service.getUserConfig("unit");
        mActivity = activity;
        mService = service;
    }

    @Override
    public ViewHolder onCreateViewHolder(final ViewGroup parent, final int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.list_element_transaction, parent, false));
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, final int position) {
        final TransactionItem txItem = mTxItems.get(position);


        final Coin coin = Coin.valueOf(txItem.amount);
        final MonetaryFormat bitcoinFormat = CurrencyMapper.mapBtcUnitToFormat(mBtcUnit);
        holder.bitcoinScale.setText(Html.fromHtml(CurrencyMapper.mapBtcUnitToPrefix(mBtcUnit)));
        if (mBtcUnit == null || mBtcUnit.equals("bits")) {
            holder.bitcoinIcon.setText("");
            holder.bitcoinScale.setText("bits ");
        } else {
            holder.bitcoinIcon.setText(Html.fromHtml("&#xf15a; "));
        }

        final String btcBalance = bitcoinFormat.noCode().format(coin).toString();

        final DecimalFormat formatter = new DecimalFormat("#,###.########");
        try {
            holder.textValue.setText(formatter.format(Double.valueOf(btcBalance)));
        } catch (final NumberFormatException e) {
            holder.textValue.setText(btcBalance);
        }

        if (!mService.isSPVEnabled() ||
            txItem.spvVerified || txItem.isSpent || txItem.type.equals(TransactionItem.TYPE.OUT)) {
            holder.textValueQuestionMark.setVisibility(View.GONE);
        } else {
            holder.textValueQuestionMark.setVisibility(View.VISIBLE);
        }

        final Resources res = mActivity.getResources();

        if (txItem.doubleSpentBy == null) {
            holder.textWhen.setTextColor(res.getColor(R.color.tertiaryTextColor));
            holder.textWhen.setText(TimeAgo.fromNow(txItem.date.getTime(), mActivity));
        } else {
            switch (txItem.doubleSpentBy) {
                case "malleability":
                    holder.textWhen.setTextColor(Color.parseColor("#FF8000"));
                    holder.textWhen.setText(res.getText(R.string.malleated));
                    break;
                case "update":
                    holder.textWhen.setTextColor(Color.parseColor("#FF8000"));
                    holder.textWhen.setText(res.getText(R.string.updated));
                    break;
                default:
                    holder.textWhen.setTextColor(Color.RED);
                    holder.textWhen.setText(res.getText(R.string.doubleSpend));
            }
        }

        holder.textReplaceable.setVisibility(txItem.replaceable ? View.VISIBLE : View.GONE);

        final boolean humanCpty = txItem.type.equals(TransactionItem.TYPE.OUT)
                && txItem.counterparty != null && txItem.counterparty.length() > 0
                && !GaService.isValidAddress(txItem.counterparty);

        final String message;
        if (TextUtils.isEmpty(txItem.memo)) {
            if (humanCpty)
                message = txItem.counterparty;
            else
                message = getTypeString(txItem.type);
        } else {
            if (humanCpty)
                message = String.format("%s %s", txItem.counterparty, txItem.memo);
            else
                message = txItem.memo;
        }

        holder.textWho.setText(message);

        final int color = txItem.amount > 0 ? R.color.superLightGreen : R.color.superLightPink;
        holder.mainLayout.setBackgroundColor(res.getColor(color));

        if (txItem.hasEnoughConfirmations()) {
            final String elem = txItem.amount > 0 ? "&#xf090;" : "&#xf08b;";
            holder.inOutIcon.setText(Html.fromHtml(elem));
            holder.listNumberConfirmation.setVisibility(View.GONE);
        } else {
            holder.inOutIcon.setText(Html.fromHtml("&#xf017;"));
            holder.listNumberConfirmation.setVisibility(View.VISIBLE);
            holder.listNumberConfirmation.setText(String.valueOf(txItem.getConfirmations()));
        }

        holder.mainLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                final Intent transactionActivity = new Intent(mActivity, TransactionActivity.class);
                transactionActivity.putExtra("TRANSACTION", txItem);
                mActivity.startActivityForResult(transactionActivity, REQUEST_TX_DETAILS);
            }
        });
    }

    private String getTypeString(final TransactionItem.TYPE type) {
        switch (type) {
            case IN:
                return mActivity.getString(R.string.txTypeIn);
            case OUT:
                return mActivity.getString(R.string.txTypeOut);
            case REDEPOSIT:
                return mActivity.getString(R.string.txTypeRedeposit);
            default:
                return "No type";
        }
    }

    @Override
    public int getItemCount() {
        return mTxItems == null ? 0 : mTxItems.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        public final TextView listNumberConfirmation;
        public final TextView textValue;
        public final TextView textWhen;
        public final TextView textReplaceable;
        public final TextView bitcoinIcon;
        public final TextView textWho;
        public final TextView inOutIcon;
        public final TextView bitcoinScale;
        public final TextView textValueQuestionMark;
        public final RelativeLayout mainLayout;

        public ViewHolder(final View itemView) {

            super(itemView);

            textValue = (TextView) itemView.findViewById(R.id.listValueText);
            textValueQuestionMark = (TextView) itemView.findViewById(R.id.listValueQuestionMark);
            textWhen = (TextView) itemView.findViewById(R.id.listWhenText);
            textReplaceable = (TextView) itemView.findViewById(R.id.listReplaceableText);
            textWho = (TextView) itemView.findViewById(R.id.listWhoText);
            inOutIcon = (TextView) itemView.findViewById(R.id.listInOutIcon);
            mainLayout = (RelativeLayout) itemView.findViewById(R.id.list_item_layout);
            bitcoinIcon = (TextView) itemView.findViewById(R.id.listBitcoinIcon);
            bitcoinScale = (TextView) itemView.findViewById(R.id.listBitcoinScaleText);
            listNumberConfirmation = (TextView) itemView.findViewById(R.id.listNumberConfirmation);
        }
    }
}
