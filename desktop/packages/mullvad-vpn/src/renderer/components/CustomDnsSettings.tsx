import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';

import { messages } from '../../shared/gettext';
import { useAppContext } from '../context';
import { Button, IconButton } from '../lib/components';
import { formatHtml } from '../lib/html-formatter';
import { IpAddress } from '../lib/ip';
import { useBoolean, useMounted, useStyledRef } from '../lib/utility-hooks';
import { useSelector } from '../redux/store';
import Accordion from './Accordion';
import {
  AriaDescribed,
  AriaDescription,
  AriaDescriptionGroup,
  AriaInput,
  AriaInputGroup,
  AriaLabel,
} from './AriaGroup';
import * as Cell from './cell';
import {
  AddServerContainer,
  StyledAddCustomDnsLabel,
  StyledButton,
  StyledCustomDnsFooter,
  StyledItemContainer,
  StyledLabel,
} from './CustomDnsSettingsStyles';
import List, { stringValueAsKey } from './List';
import { ModalAlert, ModalAlertType } from './Modal';

const manualLocal = window.env.platform === 'win32' || window.env.platform === 'linux';

export default function CustomDnsSettings() {
  const { setDnsOptions } = useAppContext();
  const dns = useSelector((state) => state.settings.dns);

  const [inputVisible, showInput, hideInput] = useBoolean(false);
  const [invalid, setInvalid, setValid] = useBoolean(false);
  const [confirmAction, setConfirmAction] = useState<() => Promise<void>>();
  const [savingAdd, setSavingAdd] = useState(false);
  const [savingEdit, setSavingEdit] = useState(false);
  const willShowConfirmationDialog = useRef(false);

  const featureAvailable = useMemo(
    () =>
      dns.state === 'custom' ||
      (!dns.defaultOptions.blockAds &&
        !dns.defaultOptions.blockTrackers &&
        !dns.defaultOptions.blockMalware &&
        !dns.defaultOptions.blockAdultContent &&
        !dns.defaultOptions.blockGambling &&
        !dns.defaultOptions.blockSocialMedia),
    [dns],
  );

  const switchRef = useStyledRef<HTMLDivElement>();
  const addButtonRef = useStyledRef<HTMLButtonElement>();
  const inputContainerRef = useStyledRef<HTMLDivElement>();

  const confirm = useCallback(() => {
    willShowConfirmationDialog.current = false;
    void confirmAction?.();
    setConfirmAction(undefined);
  }, [confirmAction]);
  const abortConfirmation = useCallback(() => {
    setConfirmAction(undefined);
    willShowConfirmationDialog.current = false;
  }, []);

  const setCustomDnsEnabled = useCallback(
    async (enabled: boolean) => {
      if (dns.customOptions.addresses.length > 0) {
        await setDnsOptions({ ...dns, state: enabled ? 'custom' : 'default' });
      }
      if (enabled && dns.customOptions.addresses.length === 0) {
        showInput();
      }
      if (!enabled) {
        hideInput();
      }
    },
    [dns, hideInput, setDnsOptions, showInput],
  );

  // The input field should be hidden when it loses focus unless something on the same row or the
  // add-button is the new focused element.
  const onInputBlur = useCallback(
    (event?: React.FocusEvent<HTMLTextAreaElement>) => {
      const relatedTarget = event?.relatedTarget as Node | undefined;
      if (
        relatedTarget &&
        (switchRef.current?.contains(relatedTarget) ||
          addButtonRef.current?.contains(relatedTarget) ||
          inputContainerRef.current?.contains(relatedTarget))
      ) {
        event?.target.focus();
      } else if (!willShowConfirmationDialog.current) {
        hideInput();
      }
    },
    [addButtonRef, hideInput, inputContainerRef, switchRef],
  );

  const onAdd = useCallback(
    async (address: string) => {
      if (dns.customOptions.addresses.includes(address)) {
        setInvalid();
      } else {
        const add = async () => {
          await setDnsOptions({
            ...dns,
            state: dns.state === 'custom' || inputVisible ? 'custom' : 'default',
            customOptions: {
              addresses: [...dns.customOptions.addresses, address],
            },
          });

          setSavingAdd(true);
          hideInput();
        };

        try {
          const ipAddress = IpAddress.fromString(address);
          if (ipAddress.isLocal() && manualLocal) {
            willShowConfirmationDialog.current = true;
            setConfirmAction(() => add);
          } else {
            await add();
          }
        } catch {
          setInvalid();
        }
      }
    },
    [dns, setInvalid, setDnsOptions, inputVisible, hideInput],
  );

  const onEdit = useCallback(
    (oldAddress: string, newAddress: string) => {
      if (oldAddress !== newAddress && dns.customOptions.addresses.includes(newAddress)) {
        throw new Error('Duplicate address');
      }

      const edit = async () => {
        setSavingEdit(true);

        const addresses = dns.customOptions.addresses.map((address) =>
          oldAddress === address ? newAddress : address,
        );
        await setDnsOptions({
          ...dns,
          customOptions: {
            addresses,
          },
        });
      };

      const ipAddress = IpAddress.fromString(newAddress);
      return new Promise<void>((resolve) => {
        if (ipAddress.isLocal() && manualLocal) {
          willShowConfirmationDialog.current = true;
          setConfirmAction(() => async () => {
            await edit();
            resolve();
          });
        } else {
          void edit().then(resolve);
        }
      });
    },
    [dns, setDnsOptions],
  );

  const onRemove = useCallback(
    (address: string) => {
      const addresses = dns.customOptions.addresses.filter((item) => item !== address);
      void setDnsOptions({
        ...dns,
        state: addresses.length > 0 && dns.state === 'custom' ? 'custom' : 'default',
        customOptions: {
          addresses,
        },
      });
    },
    [dns, setDnsOptions],
  );

  useEffect(() => setSavingEdit(false), [dns.customOptions.addresses]);
  useEffect(() => setSavingAdd(false), [dns.customOptions.addresses]);

  const listExpanded = featureAvailable && (dns.state === 'custom' || inputVisible || savingAdd);

  return (
    <>
      <Cell.Container disabled={!featureAvailable}>
        <AriaInputGroup>
          <AriaLabel>
            <Cell.InputLabel>
              {messages.pgettext('vpn-settings-view', 'Use custom DNS server')}
            </Cell.InputLabel>
          </AriaLabel>
          <AriaInput>
            <Cell.Switch
              innerRef={switchRef}
              isOn={dns.state === 'custom' || inputVisible}
              onChange={setCustomDnsEnabled}
            />
          </AriaInput>
        </AriaInputGroup>
      </Cell.Container>
      <Accordion expanded={listExpanded}>
        <Cell.Section role="listbox">
          <List
            items={dns.customOptions.addresses}
            getKey={stringValueAsKey}
            skipAddTransition={true}
            skipRemoveTransition={savingEdit}>
            {(item) => (
              <CellListItem
                onRemove={onRemove}
                onChange={onEdit}
                willShowConfirmationDialog={willShowConfirmationDialog}>
                {item}
              </CellListItem>
            )}
          </List>
        </Cell.Section>

        {inputVisible && (
          <div ref={inputContainerRef}>
            <Cell.RowInput
              placeholder={messages.pgettext('vpn-settings-view', 'Enter IP')}
              onSubmit={onAdd}
              onChange={setValid}
              invalid={invalid}
              paddingLeft={32}
              onBlur={onInputBlur}
              autofocus
            />
          </div>
        )}

        <AddServerContainer>
          <StyledButton
            ref={addButtonRef}
            onClick={showInput}
            disabled={inputVisible}
            tabIndex={-1}>
            <StyledAddCustomDnsLabel tabIndex={-1}>
              {messages.pgettext('vpn-settings-view', 'Add a server')}
            </StyledAddCustomDnsLabel>
          </StyledButton>
          <IconButton variant="secondary" onClick={showInput}>
            <IconButton.Icon icon="add-circle" />
          </IconButton>
        </AddServerContainer>
      </Accordion>

      <StyledCustomDnsFooter>
        <Cell.CellFooterText>
          {featureAvailable
            ? messages.pgettext('vpn-settings-view', 'Enable to add at least one DNS server.')
            : formatHtml(
                // TRANSLATORS: This is displayed when either or both of the block ads/trackers settings are
                // TRANSLATORS: turned on which makes the custom DNS setting disabled.
                // TRANSLATORS: Available placeholders:
                // TRANSLATORS: %(preferencesPageName)s - The page title showed on top in the preferences page.
                messages.pgettext(
                  'vpn-settings-view',
                  'Disable all <b>DNS content blockers</b> above to activate this setting.',
                ),
              )}
        </Cell.CellFooterText>
      </StyledCustomDnsFooter>

      <ConfirmationDialog
        isOpen={confirmAction !== undefined}
        confirm={confirm}
        abort={abortConfirmation}
      />
    </>
  );
}

interface ICellListItemProps {
  willShowConfirmationDialog: React.RefObject<boolean>;
  onRemove: (application: string) => void;
  onChange: (value: string, newValue: string) => Promise<void>;
  children: string;
}

function CellListItem(props: ICellListItemProps) {
  const { onRemove: propsOnRemove, onChange } = props;

  const [editing, startEditing, stopEditing] = useBoolean(false);
  const [invalid, setInvalid, setValid] = useBoolean(false);
  const isMounted = useMounted();

  const inputContainerRef = useStyledRef<HTMLDivElement>();

  const onRemove = useCallback(
    () => propsOnRemove(props.children),
    [propsOnRemove, props.children],
  );

  const onSubmit = useCallback(
    async (value: string) => {
      if (value === props.children) {
        stopEditing();
      } else {
        try {
          await onChange(props.children, value);
          if (isMounted()) {
            stopEditing();
          }
        } catch {
          setInvalid();
        }
      }
    },
    [props.children, stopEditing, onChange, isMounted, setInvalid],
  );

  const onBlur = useCallback(
    (event?: React.FocusEvent<HTMLTextAreaElement>) => {
      const relatedTarget = event?.relatedTarget as Node | undefined;
      if (relatedTarget && inputContainerRef.current?.contains(relatedTarget)) {
        event?.target.focus();
      } else if (!props.willShowConfirmationDialog.current) {
        stopEditing();
      }
    },
    [inputContainerRef, props.willShowConfirmationDialog, stopEditing],
  );

  return (
    <AriaDescriptionGroup>
      {editing ? (
        <div ref={inputContainerRef}>
          <Cell.RowInput
            initialValue={props.children}
            placeholder={messages.pgettext('vpn-settings-view', 'Enter IP')}
            onSubmit={onSubmit}
            onChange={setValid}
            invalid={invalid}
            paddingLeft={32}
            onBlur={onBlur}
            autofocus
          />
        </div>
      ) : (
        <StyledItemContainer>
          <StyledButton onClick={startEditing}>
            <AriaDescription>
              <StyledLabel>{props.children}</StyledLabel>
            </AriaDescription>
          </StyledButton>
          <AriaDescribed>
            <IconButton
              variant="secondary"
              onClick={onRemove}
              aria-label={messages.pgettext('accessibility', 'Remove item')}>
              <IconButton.Icon icon="cross-circle" />
            </IconButton>
          </AriaDescribed>
        </StyledItemContainer>
      )}
    </AriaDescriptionGroup>
  );
}

interface IConfirmationDialogProps {
  isOpen: boolean;
  confirm: () => void;
  abort: () => void;
}

function ConfirmationDialog(props: IConfirmationDialogProps) {
  const message = messages.pgettext(
    'vpn-settings-view',
    'The DNS server you want to add is a private IP. You must ensure that your network interfaces are configured to use it.',
  );
  return (
    <ModalAlert
      isOpen={props.isOpen}
      type={ModalAlertType.caution}
      buttons={[
        <Button variant="destructive" key="confirm" onClick={props.confirm}>
          <Button.Text>
            {
              // TRANSLATORS: Button label to add a private IP DNS server despite warning.
              messages.pgettext('vpn-settings-view', 'Add anyway')
            }
          </Button.Text>
        </Button>,
        <Button key="back" onClick={props.abort}>
          <Button.Text>{messages.gettext('Back')}</Button.Text>
        </Button>,
      ]}
      close={props.abort}
      message={message}
    />
  );
}
