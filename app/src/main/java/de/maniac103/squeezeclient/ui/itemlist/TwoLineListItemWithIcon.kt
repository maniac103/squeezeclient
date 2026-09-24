package de.maniac103.squeezeclient.ui.itemlist

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.maniac103.squeezeclient.R
import de.maniac103.squeezeclient.ui.Theme

@Composable
fun TwoLineListItemWithIcon(
    title: String,
    subtext: String?,
    iconResourceId: Int?,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .padding(vertical = 8.dp)
    ) {
        val iconModifier = Modifier
            .padding(horizontal = 16.dp)
            .size(40.dp)
            .align(Alignment.CenterVertically)
        if (iconResourceId != null) {
            Image(
                painter = painterResource(iconResourceId),
                contentDescription = null,
                modifier = iconModifier
            )
        } else {
            Spacer(modifier = iconModifier)
        }

        Column(
            modifier = Modifier
                .align(Alignment.CenterVertically)
                .weight(1f)
                .padding(end = 16.dp)
        ) {
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = Theme.TextStyles.listItemPrimary
            )

            if (!subtext.isNullOrEmpty()) {
                Text(
                    text = subtext,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = Theme.TextStyles.listItemSecondary
                )
            }
        }
    }
}

@Preview
@Composable
fun TwoLineListItemWithIconPreview() {
    TwoLineListItemWithIcon("Some item", "Some subtext", R.drawable.hm_albumartists)
}